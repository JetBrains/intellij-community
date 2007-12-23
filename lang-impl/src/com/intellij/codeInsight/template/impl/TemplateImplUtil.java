package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.template.Expression;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.text.CharArrayUtil;

import java.util.ArrayList;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Mar 19, 2004
 * Time: 1:28:54 PM
 * To change this template use File | Settings | File Templates.
 */
public class TemplateImplUtil {
  public static boolean validateTemplateText(String s) {
    TemplateTextLexer lexer = new TemplateTextLexer();
    lexer.start(s,0,s.length(),0);
    int end = -1;
    while(true){
      IElementType tokenType = lexer.getTokenType();
      if (tokenType == null) break;
      int start = lexer.getTokenStart();
      if (tokenType == TemplateTokenType.VARIABLE){
        if (start == end) return false;
        end = lexer.getTokenEnd();
      } else {
        end = -1;
      }
      lexer.advance();
    }
    return true;
  }

  public static void parseVariables(CharSequence text, ArrayList variables, Set predefinedVars) {
    TemplateTextLexer lexer = new TemplateTextLexer();
    lexer.start(text, 0, text.length(),0);

    while(true){
      IElementType tokenType = lexer.getTokenType();
      if (tokenType == null) break;
      int start = lexer.getTokenStart();
      int end = lexer.getTokenEnd();
      String token = text.subSequence(start, end).toString();
      if (tokenType == TemplateTokenType.VARIABLE){
        String name = token.substring(1, token.length() - 1);
        boolean isFound = false;

        if (predefinedVars!=null && predefinedVars.contains(name) && !name.equals(TemplateImpl.SELECTION)){
          isFound = true;
        }
        else{
          for (Object variable1 : variables) {
            Variable variable = (Variable)variable1;
            if (variable.getName().equals(name)) {
              isFound = true;
              break;
            }
          }
        }

        if (!isFound){
          variables.add(new Variable(name, "", "", true));
        }
      }
      lexer.advance();
    }
  }

  public static Expression parseTemplate(String text) {
    return MacroParser.parse(text);
  }
}
