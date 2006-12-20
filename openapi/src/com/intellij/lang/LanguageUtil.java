package com.intellij.lang;

import com.intellij.lexer.Lexer;

public final class LanguageUtil {
  public static ParserDefinition.SpaceRequirements canStickTokensTogetherByLexer(ASTNode left, ASTNode right, Lexer lexer, int lexerState) {
    String textStr = left.getText() + right.getText();

    lexer.start(textStr, 0, textStr.length(), lexerState);
    if(lexer.getTokenType() != left.getElementType()) return ParserDefinition.SpaceRequirements.MUST;
    if(lexer.getTokenEnd() != left.getTextLength()) return ParserDefinition.SpaceRequirements.MUST;
    lexer.advance();
    if(lexer.getTokenEnd() != textStr.length()) return ParserDefinition.SpaceRequirements.MUST;
    if(lexer.getTokenType() != right.getElementType()) return ParserDefinition.SpaceRequirements.MUST;
    return ParserDefinition.SpaceRequirements.MAY;
  }
}
