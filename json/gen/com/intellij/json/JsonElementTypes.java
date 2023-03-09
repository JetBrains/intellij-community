// This is a generated file. Not intended for manual editing.
package com.intellij.json;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.json.psi.impl.*;

public interface JsonElementTypes {

  IElementType ARRAY = new JsonElementType("ARRAY");
  IElementType BOOLEAN_LITERAL = new JsonElementType("BOOLEAN_LITERAL");
  IElementType LITERAL = new JsonElementType("LITERAL");
  IElementType NULL_LITERAL = new JsonElementType("NULL_LITERAL");
  IElementType NUMBER_LITERAL = new JsonElementType("NUMBER_LITERAL");
  IElementType OBJECT = new JsonElementType("OBJECT");
  IElementType PROPERTY = new JsonElementType("PROPERTY");
  IElementType REFERENCE_EXPRESSION = new JsonElementType("REFERENCE_EXPRESSION");
  IElementType STRING_LITERAL = new JsonElementType("STRING_LITERAL");
  IElementType VALUE = new JsonElementType("VALUE");

  IElementType BLOCK_COMMENT = new JsonTokenType("BLOCK_COMMENT");
  IElementType COLON = new JsonTokenType(":");
  IElementType COMMA = new JsonTokenType(",");
  IElementType DOUBLE_QUOTED_STRING = new JsonTokenType("DOUBLE_QUOTED_STRING");
  IElementType FALSE = new JsonTokenType("false");
  IElementType IDENTIFIER = new JsonTokenType("IDENTIFIER");
  IElementType LINE_COMMENT = new JsonTokenType("LINE_COMMENT");
  IElementType L_BRACKET = new JsonTokenType("[");
  IElementType L_CURLY = new JsonTokenType("{");
  IElementType NULL = new JsonTokenType("null");
  IElementType NUMBER = new JsonTokenType("NUMBER");
  IElementType R_BRACKET = new JsonTokenType("]");
  IElementType R_CURLY = new JsonTokenType("}");
  IElementType SINGLE_QUOTED_STRING = new JsonTokenType("SINGLE_QUOTED_STRING");
  IElementType TRUE = new JsonTokenType("true");

  class Factory {
    public static PsiElement createElement(ASTNode node) {
      IElementType type = node.getElementType();
      if (type == ARRAY) {
        return new JsonArrayImpl(node);
      }
      else if (type == BOOLEAN_LITERAL) {
        return new JsonBooleanLiteralImpl(node);
      }
      else if (type == NULL_LITERAL) {
        return new JsonNullLiteralImpl(node);
      }
      else if (type == NUMBER_LITERAL) {
        return new JsonNumberLiteralImpl(node);
      }
      else if (type == OBJECT) {
        return new JsonObjectImpl(node);
      }
      else if (type == PROPERTY) {
        return new JsonPropertyImpl(node);
      }
      else if (type == REFERENCE_EXPRESSION) {
        return new JsonReferenceExpressionImpl(node);
      }
      else if (type == STRING_LITERAL) {
        return new JsonStringLiteralImpl(node);
      }
      throw new AssertionError("Unknown element type: " + type);
    }
  }
}
