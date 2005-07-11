/*
 * Copyright (c) 2000-05 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.lang.cacheBuilder;

import com.intellij.lexer.Lexer;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.Processor;
import com.intellij.util.text.CharArrayCharSequence;
import com.intellij.util.text.CharArrayUtil;

/**
 * The default implementation of a words scanner based on a custom language lexer.
 *
 * @author max
 */

public class DefaultWordsScanner implements WordsScanner {
  private Lexer myLexer;
  private TokenSet myIdentifierTokenSet;
  private TokenSet myCommentTokenSet;
  private TokenSet myLiteralTokenSet;

  /**
   * Creates a new instance of the words scanner.
   *
   * @param lexer              the lexer used for breaking the text into tokens.
   * @param identifierTokenSet the set of token types which represent identifiers.
   * @param commentTokenSet    the set of token types which represent comments.
   * @param literalTokenSet    the set of token types which represent literals.
   */
  public DefaultWordsScanner(final Lexer lexer, final TokenSet identifierTokenSet, final TokenSet commentTokenSet,
                             final TokenSet literalTokenSet) {
    myLexer = lexer;
    myIdentifierTokenSet = identifierTokenSet;
    myCommentTokenSet = commentTokenSet;
    myLiteralTokenSet = literalTokenSet;
  }

  public void processWords(CharSequence fileText, Processor<WordOccurrence> processor) {
    char[] chars = CharArrayUtil.fromSequence(fileText);
    myLexer.start(chars, 0, fileText.length());
    while (myLexer.getTokenType() != null) {
      final IElementType type = myLexer.getTokenType();
      if (myIdentifierTokenSet.isInSet(type)) {
        if (!processor.process(new WordOccurrence(currentTokenText(chars), WordOccurrence.Kind.CODE))) return;
      }
      else if (myCommentTokenSet.isInSet(type)) {
        if (!stripWords(processor, currentTokenText(chars), WordOccurrence.Kind.COMMENTS)) return;
      }
      else if (myLiteralTokenSet.isInSet(type)) {
        if (!stripWords(processor, currentTokenText(chars), WordOccurrence.Kind.LITERALS)) return;
      }
      myLexer.advance();
    }
  }

  private static boolean stripWords(final Processor<WordOccurrence> processor,
                                    final CharArrayCharSequence tokenText,
                                    final WordOccurrence.Kind kind) {
    // This code seems strange but it is more effective as Character.isJavaIdentifier_xxx_ is quite costly operation due to unicode
    int index = 0;

    ScanWordsLoop:
    while (true) {
      while (true) {
        if (index == tokenText.length()) break ScanWordsLoop;
        char c = tokenText.charAt(index);
        if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') ||
            (Character.isJavaIdentifierStart(c) && c != '$')) {
          break;
        }
        index++;
      }
      int index1 = index;
      while (true) {
        index++;
        if (index == tokenText.length()) break;
        char c = tokenText.charAt(index);
        if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) continue;
        if (!Character.isJavaIdentifierPart(c) || c == '$') break;
      }

      if (!processor.process(new WordOccurrence(tokenText.subSequence(index1, index), kind))) return false;
    }
    return true;
  }

  private CharArrayCharSequence currentTokenText(final char[] chars) {
    return new CharArrayCharSequence(chars, myLexer.getTokenStart(), myLexer.getTokenEnd());
  }
}
