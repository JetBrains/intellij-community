// This is a generated file. Not intended for manual editing.
package com.intellij.jsonpath.psi;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.jsonpath.psi.impl.*;

public interface JsonPathTypes {

  IElementType AND_EXPRESSION = new JsonPathElementType("AND_EXPRESSION");
  IElementType ARRAY_VALUE = new JsonPathElementType("ARRAY_VALUE");
  IElementType BINARY_CONDITIONAL_OPERATOR = new JsonPathElementType("BINARY_CONDITIONAL_OPERATOR");
  IElementType BOOLEAN_LITERAL = new JsonPathElementType("BOOLEAN_LITERAL");
  IElementType CONDITIONAL_EXPRESSION = new JsonPathElementType("CONDITIONAL_EXPRESSION");
  IElementType DIVIDE_EXPRESSION = new JsonPathElementType("DIVIDE_EXPRESSION");
  IElementType EVAL_SEGMENT = new JsonPathElementType("EVAL_SEGMENT");
  IElementType EXPRESSION = new JsonPathElementType("EXPRESSION");
  IElementType FILTER_EXPRESSION = new JsonPathElementType("FILTER_EXPRESSION");
  IElementType FUNCTION_ARGS_LIST = new JsonPathElementType("FUNCTION_ARGS_LIST");
  IElementType FUNCTION_CALL = new JsonPathElementType("FUNCTION_CALL");
  IElementType ID = new JsonPathElementType("ID");
  IElementType ID_SEGMENT = new JsonPathElementType("ID_SEGMENT");
  IElementType INDEXES_LIST = new JsonPathElementType("INDEXES_LIST");
  IElementType INDEX_EXPRESSION = new JsonPathElementType("INDEX_EXPRESSION");
  IElementType LITERAL_VALUE = new JsonPathElementType("LITERAL_VALUE");
  IElementType MINUS_EXPRESSION = new JsonPathElementType("MINUS_EXPRESSION");
  IElementType MULTIPLY_EXPRESSION = new JsonPathElementType("MULTIPLY_EXPRESSION");
  IElementType NULL_LITERAL = new JsonPathElementType("NULL_LITERAL");
  IElementType NUMBER_LITERAL = new JsonPathElementType("NUMBER_LITERAL");
  IElementType OBJECT_PROPERTY = new JsonPathElementType("OBJECT_PROPERTY");
  IElementType OBJECT_VALUE = new JsonPathElementType("OBJECT_VALUE");
  IElementType OR_EXPRESSION = new JsonPathElementType("OR_EXPRESSION");
  IElementType PARENTHESIZED_EXPRESSION = new JsonPathElementType("PARENTHESIZED_EXPRESSION");
  IElementType PATH_EXPRESSION = new JsonPathElementType("PATH_EXPRESSION");
  IElementType PLUS_EXPRESSION = new JsonPathElementType("PLUS_EXPRESSION");
  IElementType QUOTED_PATHS_LIST = new JsonPathElementType("QUOTED_PATHS_LIST");
  IElementType QUOTED_SEGMENT = new JsonPathElementType("QUOTED_SEGMENT");
  IElementType REGEX_EXPRESSION = new JsonPathElementType("REGEX_EXPRESSION");
  IElementType REGEX_LITERAL = new JsonPathElementType("REGEX_LITERAL");
  IElementType ROOT_SEGMENT = new JsonPathElementType("ROOT_SEGMENT");
  IElementType SEGMENT_EXPRESSION = new JsonPathElementType("SEGMENT_EXPRESSION");
  IElementType SLICE_EXPRESSION = new JsonPathElementType("SLICE_EXPRESSION");
  IElementType STRING_LITERAL = new JsonPathElementType("STRING_LITERAL");
  IElementType UNARY_MINUS_EXPRESSION = new JsonPathElementType("UNARY_MINUS_EXPRESSION");
  IElementType UNARY_NOT_EXPRESSION = new JsonPathElementType("UNARY_NOT_EXPRESSION");
  IElementType VALUE = new JsonPathElementType("VALUE");
  IElementType WILDCARD_SEGMENT = new JsonPathElementType("WILDCARD_SEGMENT");

  IElementType AND_OP = new JsonPathTokenType("AND_OP");
  IElementType COLON = new JsonPathTokenType(":");
  IElementType COMMA = new JsonPathTokenType(",");
  IElementType DIVIDE_OP = new JsonPathTokenType("DIVIDE_OP");
  IElementType DOT = new JsonPathTokenType(".");
  IElementType DOUBLE_NUMBER = new JsonPathTokenType("DOUBLE_NUMBER");
  IElementType DOUBLE_QUOTED_STRING = new JsonPathTokenType("DOUBLE_QUOTED_STRING");
  IElementType EEQ_OP = new JsonPathTokenType("EEQ_OP");
  IElementType ENE_OP = new JsonPathTokenType("ENE_OP");
  IElementType EQ_OP = new JsonPathTokenType("EQ_OP");
  IElementType EVAL_CONTEXT = new JsonPathTokenType("@");
  IElementType FALSE = new JsonPathTokenType("false");
  IElementType FILTER_OPERATOR = new JsonPathTokenType("?");
  IElementType GE_OP = new JsonPathTokenType("GE_OP");
  IElementType GT_OP = new JsonPathTokenType("GT_OP");
  IElementType IDENTIFIER = new JsonPathTokenType("IDENTIFIER");
  IElementType INTEGER_NUMBER = new JsonPathTokenType("INTEGER_NUMBER");
  IElementType LBRACE = new JsonPathTokenType("{");
  IElementType LBRACKET = new JsonPathTokenType("[");
  IElementType LE_OP = new JsonPathTokenType("LE_OP");
  IElementType LPARENTH = new JsonPathTokenType("(");
  IElementType LT_OP = new JsonPathTokenType("LT_OP");
  IElementType MINUS_OP = new JsonPathTokenType("MINUS_OP");
  IElementType MULTIPLY_OP = new JsonPathTokenType("MULTIPLY_OP");
  IElementType NAMED_OP = new JsonPathTokenType("NAMED_OP");
  IElementType NE_OP = new JsonPathTokenType("NE_OP");
  IElementType NOT_OP = new JsonPathTokenType("NOT_OP");
  IElementType NULL = new JsonPathTokenType("null");
  IElementType OR_OP = new JsonPathTokenType("OR_OP");
  IElementType PLUS_OP = new JsonPathTokenType("PLUS_OP");
  IElementType RBRACE = new JsonPathTokenType("}");
  IElementType RBRACKET = new JsonPathTokenType("]");
  IElementType RECURSIVE_DESCENT = new JsonPathTokenType("..");
  IElementType REGEX_STRING = new JsonPathTokenType("REGEX_STRING");
  IElementType RE_OP = new JsonPathTokenType("RE_OP");
  IElementType ROOT_CONTEXT = new JsonPathTokenType("$");
  IElementType RPARENTH = new JsonPathTokenType(")");
  IElementType SINGLE_QUOTED_STRING = new JsonPathTokenType("SINGLE_QUOTED_STRING");
  IElementType TRUE = new JsonPathTokenType("true");
  IElementType WILDCARD = new JsonPathTokenType("*");

  class Factory {
    public static PsiElement createElement(ASTNode node) {
      IElementType type = node.getElementType();
      if (type == AND_EXPRESSION) {
        return new JsonPathAndExpressionImpl(node);
      }
      else if (type == ARRAY_VALUE) {
        return new JsonPathArrayValueImpl(node);
      }
      else if (type == BINARY_CONDITIONAL_OPERATOR) {
        return new JsonPathBinaryConditionalOperatorImpl(node);
      }
      else if (type == BOOLEAN_LITERAL) {
        return new JsonPathBooleanLiteralImpl(node);
      }
      else if (type == CONDITIONAL_EXPRESSION) {
        return new JsonPathConditionalExpressionImpl(node);
      }
      else if (type == DIVIDE_EXPRESSION) {
        return new JsonPathDivideExpressionImpl(node);
      }
      else if (type == EVAL_SEGMENT) {
        return new JsonPathEvalSegmentImpl(node);
      }
      else if (type == FILTER_EXPRESSION) {
        return new JsonPathFilterExpressionImpl(node);
      }
      else if (type == FUNCTION_ARGS_LIST) {
        return new JsonPathFunctionArgsListImpl(node);
      }
      else if (type == FUNCTION_CALL) {
        return new JsonPathFunctionCallImpl(node);
      }
      else if (type == ID) {
        return new JsonPathIdImpl(node);
      }
      else if (type == ID_SEGMENT) {
        return new JsonPathIdSegmentImpl(node);
      }
      else if (type == INDEXES_LIST) {
        return new JsonPathIndexesListImpl(node);
      }
      else if (type == INDEX_EXPRESSION) {
        return new JsonPathIndexExpressionImpl(node);
      }
      else if (type == MINUS_EXPRESSION) {
        return new JsonPathMinusExpressionImpl(node);
      }
      else if (type == MULTIPLY_EXPRESSION) {
        return new JsonPathMultiplyExpressionImpl(node);
      }
      else if (type == NULL_LITERAL) {
        return new JsonPathNullLiteralImpl(node);
      }
      else if (type == NUMBER_LITERAL) {
        return new JsonPathNumberLiteralImpl(node);
      }
      else if (type == OBJECT_PROPERTY) {
        return new JsonPathObjectPropertyImpl(node);
      }
      else if (type == OBJECT_VALUE) {
        return new JsonPathObjectValueImpl(node);
      }
      else if (type == OR_EXPRESSION) {
        return new JsonPathOrExpressionImpl(node);
      }
      else if (type == PARENTHESIZED_EXPRESSION) {
        return new JsonPathParenthesizedExpressionImpl(node);
      }
      else if (type == PATH_EXPRESSION) {
        return new JsonPathPathExpressionImpl(node);
      }
      else if (type == PLUS_EXPRESSION) {
        return new JsonPathPlusExpressionImpl(node);
      }
      else if (type == QUOTED_PATHS_LIST) {
        return new JsonPathQuotedPathsListImpl(node);
      }
      else if (type == QUOTED_SEGMENT) {
        return new JsonPathQuotedSegmentImpl(node);
      }
      else if (type == REGEX_EXPRESSION) {
        return new JsonPathRegexExpressionImpl(node);
      }
      else if (type == REGEX_LITERAL) {
        return new JsonPathRegexLiteralImpl(node);
      }
      else if (type == ROOT_SEGMENT) {
        return new JsonPathRootSegmentImpl(node);
      }
      else if (type == SEGMENT_EXPRESSION) {
        return new JsonPathSegmentExpressionImpl(node);
      }
      else if (type == SLICE_EXPRESSION) {
        return new JsonPathSliceExpressionImpl(node);
      }
      else if (type == STRING_LITERAL) {
        return new JsonPathStringLiteralImpl(node);
      }
      else if (type == UNARY_MINUS_EXPRESSION) {
        return new JsonPathUnaryMinusExpressionImpl(node);
      }
      else if (type == UNARY_NOT_EXPRESSION) {
        return new JsonPathUnaryNotExpressionImpl(node);
      }
      else if (type == WILDCARD_SEGMENT) {
        return new JsonPathWildcardSegmentImpl(node);
      }
      throw new AssertionError("Unknown element type: " + type);
    }
  }
}
