/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
      if (myIdentifierTokenSet.contains(type)) {
        if (!processor.process(new WordOccurrence(currentTokenText(chars), WordOccurrence.Kind.CODE))) return;
      }
      else if (myCommentTokenSet.contains(type)) {
        if (!stripWords(processor, currentTokenText(chars), WordOccurrence.Kind.COMMENTS)) return;
      }
      else if (myLiteralTokenSet.contains(type)) {
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
