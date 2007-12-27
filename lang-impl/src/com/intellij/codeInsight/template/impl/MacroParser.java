package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.Macro;
import com.intellij.codeInsight.template.macro.MacroFactory;
import com.intellij.lexer.JavaLexer;
import com.intellij.lexer.Lexer;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.tree.IElementType;

/**
 *
 */
class MacroParser {
  private MacroParser() {
  }

  //-----------------------------------------------------------------------------------
  public static Expression parse(String expression) {
    if(expression.length() == 0) {
      return new ConstantNode("");
    }
    Lexer lexer = new JavaLexer(LanguageLevel.JDK_1_3);
    lexer.start(expression, 0, expression.length(),0);
    skipWhitespaces(lexer);
    return parseMacro(lexer, expression);
  }
//-----------------------------------------------------------------------------------
  private static void advance(Lexer lexer) {
    lexer.advance();
    skipWhitespaces(lexer);
  }

//-----------------------------------------------------------------------------------
  private static void skipWhitespaces(Lexer lexer) {
    while(lexer.getTokenType() == JavaTokenType.WHITE_SPACE) {
      lexer.advance();
    }
  }
//-----------------------------------------------------------------------------------
  private static String getString(Lexer lexer, String expression) {
    return expression.substring(lexer.getTokenStart(), lexer.getTokenEnd());
  }

//-----------------------------------------------------------------------------------
  @SuppressWarnings({"HardCodedStringLiteral"})
  private static Expression parseMacro(Lexer lexer, String expression) {
    IElementType tokenType = lexer.getTokenType();
    String token = getString(lexer, expression);
    if(tokenType == JavaTokenType.STRING_LITERAL) {
      advance(lexer);

      return new ConstantNode(token.substring(1, token.length()-1).replaceAll("\\\\n", "\n").
                              replaceAll("\\\\r", "\r").replaceAll("\\\\t", "\t").replaceAll("\\\\f", "\f").replaceAll("\\\\(.)", "$1"));
    }

    if(tokenType != JavaTokenType.IDENTIFIER) {
      System.out.println("Bad macro syntax: Not identifier: " + token);
      advance(lexer);
      return new ConstantNode("");
    }

    Macro macro = MacroFactory.createMacro(token);
    if(macro == null) {
      return parseVariable(lexer, expression);
    }

    advance(lexer);
    MacroCallNode macroCallNode = new MacroCallNode(macro);
    if(lexer.getTokenType() == null) {
      return macroCallNode;
    }

    String token2 = getString(lexer, expression);
    if(!token2.equals("(")) {
      return macroCallNode;
    }

    advance(lexer);
    parseParameters(macroCallNode, lexer, expression);
    String token3 = getString(lexer, expression);
    if(!token3.equals(")")) {
      System.out.println("Bad macro syntax: ) expected: " + expression);
    }
    advance(lexer);
    return macroCallNode;
  }
  private static void parseParameters(MacroCallNode macroCallNode, Lexer lexer, String expression) {
    if (!getString(lexer, expression).equals(")")) {
      while (lexer.getTokenType() != null) {
        Expression node = parseMacro(lexer, expression);
        macroCallNode.addParameter(node);
        String token = getString(lexer, expression);

        if (token.equals(",")) {
          advance(lexer);
        } else {
          break;
        }
      }
    }
  }

  private static Expression parseVariable(Lexer lexer, String expression) {
    String variableName = getString(lexer, expression);
    advance(lexer);

    if(lexer.getTokenType() == null) {
      if(TemplateImpl.END.equals(variableName)) {
        return new EmptyNode();
      }

      return new VariableNode(variableName, null);
    }

    String token = getString(lexer, expression);
    if(!token.equals("=")) {
      return new VariableNode(variableName, null);
    }

    advance(lexer);
    Expression node = parseMacro(lexer, expression);
    return new VariableNode(variableName, node);
  }
}
