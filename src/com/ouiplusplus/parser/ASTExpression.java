package com.ouiplusplus.parser;
import com.ouiplusplus.error.*;
import com.ouiplusplus.error.Error;
import com.ouiplusplus.helper.Pair;
import com.ouiplusplus.lexer.*;

import java.util.LinkedList;
import java.util.Queue;


import java.util.List;

public class ASTExpression {
    /* DETAILS
    * PEMDAS WITH LEFT ON BOTTOM OF TREE
    * LPAREN AND INT/DOUBLE CAN BE NEGATIVE
    * LPAREN OPEN AT START THEN CLOSED LATER ON
    *
    * POSSIBLE ERRORS:
    * OVERFLOW
    * DIVIDE BY 0
    * EMPTY PARENTHESES*/
    public TreeNode root;
    private Parser parser; //for functions and variables
    private int opened; //number of opened parentheses
    private Position start;
    private Position end;
    private int size;

    //############## CLASS METHODS #######################
    public ASTExpression(Parser parser) {
        this.parser = parser;
        this.opened = 0;
    }


    private Error addVal(Token token) { //null for no errors
        Error err = new UnexpectedToken(token.getStart(), token.getEnd(), token.getValue());

        TokenType tt = token.getType();
        this.size++;
        return switch (tt) {
            case VAR, FUNCCALL, DOUBLE, INT -> caseNUM(token);
            case MULT, DIV -> caseMULTDIV(token);
            case PLUS, MINUS -> casePLUSMINUS(token);
            case LPAREN -> caseLPAREN(token);
            case RPAREN -> caseRPAREN(token);
            default -> err;
        };
    }

    public Error addList(List<Token> tokens) {
        Error err;
        if (!tokens.isEmpty()) {
            this.start = tokens.get(0).getStart();
            this.end = tokens.get(tokens.size() - 1).getEnd();
        }
        for(Token tok: tokens) {
            err = this.addVal(tok);
            if (err != null) {
                return err;
            }
        }
        return null;
    }

    public Pair<Token, Error> resolveTreeVal() {
        Pair<Token, Error> nullVal = new Pair<>(null, new Error("No Input Given"));
        if (this.root == null) return nullVal;

        OverFlow over = new OverFlow(this.start.copy(),this.end.copy(), null);
        Pair<Token, Error> err = new Pair<>(null, over);
        if (this.opened != 0) return err;
        Token fnlVal = this.dfsResolveVal(this.root);
        if (fnlVal == null || fnlVal.getValue() == null) return err;
        return new Pair<>(fnlVal, null);
    }

    @Override
    public String toString() {
        if (this.root == null) return "";
        return this.dfsToString(this.root);
    }

    public void clearTree() {
        this.root = null;
    }



    //############## END CLASS METHODS ####################

    // ############## LIST OF ALL CASES ###################
    private Error caseLPAREN(Token token) {
        if (this.root == null) { //case root null
            this.root = new TreeNode(token);
            this.opened++;
            return null;
        }

        //setting currNode
        TreeNode currNode;
        if (this.opened != 0) currNode = this.returnBottomOpenParen();
        else currNode = this.root;

        //case LPAREN we are at has no leaves
        if (currNode.token.getType() == TokenType.LPAREN && currNode.left == null) {
            currNode.left = new TreeNode(token);
            this.opened++;
            return null;
        }

        //finding entry point and adding in value
        // Traverses down right side of tree until null right leaf found
        while (true) {
            if (currNode.right == null) {
                currNode.right = new TreeNode(token);
                this.opened++;
                return null;
            }
            currNode = currNode.right;
        }
    }
    private Error caseRPAREN(Token token) {
        Error err = new UnclosedParenthesis(token.getStart(), token.getEnd(), "()");
        if (this.root == null) return err;

        TreeNode currNode;
        if (this.opened != 0) {
            currNode = this.returnBottomOpenParen();
            Error unexpected = new UnclosedParenthesis(currNode.token.getStart(), token.getEnd(), "()");
            if (currNode.left == null) return unexpected; //if parenthesis pair with nothing inside
            Token tmp = currNode.token;
            currNode.token = new Token(TokenType.CLOSEDPAREN, "()", currNode.token.getStart(), token.getEnd());
            if (tmp.isNeg()) currNode.token.setNeg(true);
            this.opened--;
            return null;
        }
        return err;
    }
    private Error caseNUM(Token token) {
        if (this.root == null) { //case root null
            this.root = new TreeNode(token);
            return null;
        }

        //setting currNode
        TreeNode currNode;
        if (this.opened != 0) currNode = this.returnBottomOpenParen();
        else currNode = this.root;

        //case LPAREN we are at has no leaves
        if (currNode.token.getType() == TokenType.LPAREN && currNode.left == null) {
            currNode.left = new TreeNode(token);
            return null;
        }


        //finding entry point and adding in value
        // Traverses down right side of tree until null right leaf found
        while (true) {
            if (currNode.right == null && isOp(currNode.token.getType())) {
                currNode.right = new TreeNode(token);
                return null;
            }
            if (isOp(currNode.token.getType())) {
                currNode = currNode.right;
            } else {
                currNode = currNode.left;
            }

        }
    }
    private Error caseMULTDIV(Token token) {
        //setting currNode
        TreeNode currNode;
        if (this.opened != 0) currNode = this.returnBottomOpenParen();
        else currNode = this.root;

        //finding entry point and adding in value
        // Traverses down right side of tree until null right leaf found
        while (true) {
            if (currNode.right == null) {
                TreeNode tmpLeft = currNode.left;
                if (currNode.token.getType() == TokenType.LPAREN) {
                    currNode.left = new TreeNode(token);
                } else {
                    Token tmp = currNode.token;
                    currNode.token = token;
                    currNode.left = new TreeNode(tmp);
                }
                currNode.left.left = tmpLeft;
                return null;
            }
            currNode = currNode.right;
        }
    }
    private Error casePLUSMINUS(Token token) {
        //setting currNode
        TreeNode currNode;
        if (this.opened != 0)  {
            currNode = this.returnBottomOpenParen();
            TreeNode tmpLeft = currNode.left;
            currNode.left = new TreeNode(token);
            currNode.left.left = tmpLeft;
        }
        else {
            TreeNode tmp = new TreeNode(token);
            tmp.left = this.root;
            this.root = tmp;
        }
        return null;
    }
    // ################# END OF CASES ###########################


    // ################## HELPER METHODS #########################

    public TreeNode returnBottomOpenParen() {
        if (this.opened == 0) return null;
        Queue<TreeNode> q = new LinkedList<>();
        int leftToDiscover = this.opened;
        q.add(this.root);
        while (q.size() != 0) {
            TreeNode curr = q.remove();
            //if LPAREN
            if (curr.token.getType() == TokenType.LPAREN) leftToDiscover--;
            if (leftToDiscover == 0) return curr;

            //adding left and right nodes
            if (curr.left != null) q.add(curr.left);
            if (curr.right != null) q.add(curr.right);
        }
        return null;
    }

    private static boolean isOp(TokenType tt) {
        return tt == TokenType.MULT
                || tt == TokenType.DIV
                || tt == TokenType.MINUS
                || tt == TokenType.PLUS;
    }

    private static Token combineTokens(Token left, Token op, Token right) {
        if (left == null || op == null || right == null) return null; //case of overflowError

        if (left.isNeg()) left.setValue("-" + left.getValue());
        if (right.isNeg()) right.setValue("-" + right.getValue());

        if (left.getType() == TokenType.STRING || right.getType() == TokenType.STRING) {
            Token rtn = new Token(TokenType.STRING);
            rtn.setValue(left.getValue() + right.getValue());
        } else if (left.getType() == TokenType.DOUBLE || right.getType() == TokenType.DOUBLE) {
            Token rtnTok = new Token(TokenType.DOUBLE);
            try {
                double val;
                double leftVal = Double.parseDouble(left.getValue());
                double rightVal = Double.parseDouble(right.getValue());
                if (op.getType() == TokenType.PLUS) val = leftVal + rightVal;
                else if (op.getType() == TokenType.MINUS) val = leftVal - rightVal;
                else if (op.getType() == TokenType.MULT) val = leftVal * rightVal;
                else if (op.getType() == TokenType.DIV) {
                    if (rightVal == 0) return null;
                    val = leftVal / rightVal;
                } else return null;

                // if val negative
                if (val < 0) {
                    val = val * (-1);
                    rtnTok.setNeg(true);
                }
                rtnTok.setValue(Double.toString(val));
                return rtnTok;
            } catch(Exception e) {
                return null;
            }

        } else if (left.getType() == TokenType.INT || right.getType() == TokenType.INT) {
            Token rtnTok = new Token(TokenType.INT);
            try{
                int val;
                int leftVal = Integer.parseInt(left.getValue());
                int rightVal = Integer.parseInt(right.getValue());
                if (op.getType() == TokenType.PLUS) val = leftVal + rightVal;
                else if (op.getType() == TokenType.MINUS) val = leftVal - rightVal;
                else if (op.getType() == TokenType.MULT) val = leftVal * rightVal;
                else if (op.getType() == TokenType.DIV) {
                    if (rightVal == 0) return null;
                    val = leftVal / rightVal;
                } else return null;

                // if val negative
                if (val < 0) {
                    val = val * (-1);
                    rtnTok.setNeg(true);
                }
                rtnTok.setValue(Integer.toString(val));
                return rtnTok;
            } catch(Exception e) {
                return null;
            }
        }

        return null;
    }

    private Token dfsResolveVal(TreeNode node) {
        if (node == null) return null; //for overflowError
        Token tmp;
        if (node.right != null && node.left != null) {
            Token left = this.dfsResolveVal(node.left);
            Token right = this.dfsResolveVal(node.right);
            tmp = combineTokens(left, node.token, right);
        } else if (node.left != null) { // case of ()
            tmp = this.dfsResolveVal(node.left);
            if(node.token.isNeg()) tmp.setNeg(!tmp.isNeg());
        }
        else return node.token;
        return tmp;
    }

    private String dfsToString(TreeNode node) {
        String tmp = "";
        if (node.right != null && node.left != null) {
            tmp += "("  + this.dfsToString(node.left);
            tmp += node.token + this.dfsToString(node.right) + ")";
        } else if (node.left != null) { // case of ()
            tmp += "(" + this.dfsToString(node.left) + ")";
        }
        else return "[" + node.token + "]";
        return tmp;
    }

    // ################### END HELPER METHODS #####################


    // ################### TREE CLASS #############################
    class TreeNode {
        public Token token;
        public TreeNode right, left;

        public TreeNode(Token token) {
            this.token = token;
        }
    }
    // #################### END TREE CLASS ###########################
}