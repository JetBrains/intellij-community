/*
 * @author max
 */
package com.intellij.psi.search.scope.packageSet.lexer;

import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;

public interface ScopeTokenTypes extends TokenType {
  IElementType IDENTIFIER = new ScopeTokenType("Scope.IDENTIFIER");
  IElementType INTEGER_LITERAL = new ScopeTokenType("Scope.INTEGER_LITERAL");
  IElementType OROR = new ScopeTokenType("Scope.OROR");
  IElementType ANDAND = new ScopeTokenType("Scope.ANDAND");
  IElementType EXCL = new ScopeTokenType("Scope.EXCL");
  IElementType MINUS = new ScopeTokenType("Scope.MINUS");
  IElementType LBRACKET = new ScopeTokenType("Scope.LBRACKET");
  IElementType RBRACKET = new ScopeTokenType("Scope.RBRACKET");
  IElementType LPARENTH = new ScopeTokenType("Scope.LPARENTH");
  IElementType RPARENTH = new ScopeTokenType("Scope.RPARENTH");
  IElementType DOT = new ScopeTokenType("Scope.DOT");
  IElementType COLON = new ScopeTokenType("Scope.COLON");
  IElementType ASTERISK = new ScopeTokenType("Scope.ASTERISK");
  IElementType DIV = new ScopeTokenType("Scope.DIV");
}