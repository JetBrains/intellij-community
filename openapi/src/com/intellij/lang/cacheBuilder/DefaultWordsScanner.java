package com.intellij.lang.cacheBuilder;

import com.intellij.lexer.Lexer;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.Processor;
import com.intellij.util.text.CharArrayCharSequence;
import com.intellij.util.text.CharArrayUtil;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 31, 2005
 * Time: 9:12:33 PM
 * To change this template use File | Settings | File Templates.
 */
public class DefaultWordsScanner implements WordsScanner {
  private Lexer myLexer;
  private TokenSet myIdentifierTokenSet;
  private TokenSet myCommentTokenSet;
  private TokenSet myLiteralTokenSet;

  public DefaultWordsScanner(final Lexer lexer, final TokenSet identifierTokenSet, final TokenSet commentTokenSet, final TokenSet literalTokenSet) {
    myLexer = lexer;
    myIdentifierTokenSet = identifierTokenSet;
    myCommentTokenSet = commentTokenSet;
    myLiteralTokenSet = literalTokenSet;
  }

  public void processWords(CharSequence fileText, Processor<WordOccurence> processor) {
    char[] chars = CharArrayUtil.fromSequence(fileText);
    myLexer.start(chars, 0, fileText.length());
    while (myLexer.getTokenType() != null) {
      final IElementType type = myLexer.getTokenType();
      if (myIdentifierTokenSet.isInSet(type)) {
        if (!processor.process(new WordOccurence(currentTokenText(chars), WordOccurence.Kind.CODE))) return;
      }
      else if (myCommentTokenSet.isInSet(type)) {
        if (!stripWords(processor, currentTokenText(chars), WordOccurence.Kind.COMMENTS)) return;
      }
      else if (myLiteralTokenSet.isInSet(type)) {
        if (!stripWords(processor, currentTokenText(chars), WordOccurence.Kind.LITERALS)) return;
      }
      myLexer.advance();
    }
  }

  /**
   * This code seems strange but it is more effective as Character.isJavaIdentifier_xxx_ is quite costy operation due to unicode
   */
  private static boolean stripWords(final Processor<WordOccurence> processor,
                                    final CharArrayCharSequence tokenText,
                                    final WordOccurence.Kind kind) {
    int index = 0;

    ScanWordsLoop:
      while(true){
        while(true){
          if (index == tokenText.length()) break ScanWordsLoop;
          char c = tokenText.charAt(index);
          if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || (Character.isJavaIdentifierStart(c) && c != '$')) break;
          index++;
        }
        int index1 = index;
        while(true){
          index++;
          if (index == tokenText.length()) break;
          char c = tokenText.charAt(index);
          if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) continue;
          if (!Character.isJavaIdentifierPart(c) || c == '$') break;
        }

        if (!processor.process(new WordOccurence(tokenText.subSequence(index1, index), kind))) return false;
      }
    return true;
  }

  private CharArrayCharSequence currentTokenText(final char[] chars) {
    return new CharArrayCharSequence(chars, myLexer.getTokenStart(), myLexer.getTokenEnd());
  }
}
