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
  private boolean myMayHaveFileRefsInLiterals;

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
    myLexer.start(fileText, 0, fileText.length(),0);
    WordOccurrence occurence = null; // shared occurence

    while (myLexer.getTokenType() != null) {
      final IElementType type = myLexer.getTokenType();
      if (myIdentifierTokenSet.contains(type)) {
        if (occurence == null) occurence = new WordOccurrence(fileText,myLexer.getTokenStart(),myLexer.getTokenEnd(), WordOccurrence.Kind.CODE);
        else occurence.init(fileText,myLexer.getTokenStart(),myLexer.getTokenEnd(), WordOccurrence.Kind.CODE);
        if (!processor.process(occurence)) return;
      }
      else if (myCommentTokenSet.contains(type)) {
        if (!stripWords(processor, fileText,myLexer.getTokenStart(),myLexer.getTokenEnd(), WordOccurrence.Kind.COMMENTS,occurence, false)) return;
      }
      else if (myLiteralTokenSet.contains(type)) {
        if (!stripWords(processor, fileText, myLexer.getTokenStart(),myLexer.getTokenEnd(),WordOccurrence.Kind.LITERALS,occurence, myMayHaveFileRefsInLiterals)) return;
      }
      myLexer.advance();
    }
  }

  private static boolean stripWords(final Processor<WordOccurrence> processor,
                                    final CharSequence tokenText,
                                    int from,
                                    int to,
                                    final WordOccurrence.Kind kind,
                                    WordOccurrence occurence,
                                    boolean mayHaveFileRefs
  ) {
    // This code seems strange but it is more effective as Character.isJavaIdentifier_xxx_ is quite costly operation due to unicode
    int index = from;

    ScanWordsLoop:
    while (true) {
      while (true) {
        if (index == to) break ScanWordsLoop;
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
        if (index == to) break;
        char c = tokenText.charAt(index);
        if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) continue;
        if (!Character.isJavaIdentifierPart(c) || c == '$') break;
      }

      if (occurence == null) occurence = new WordOccurrence(tokenText,index1, index, kind);
      else occurence.init(tokenText,index1, index, kind);

      if (!processor.process(occurence)) return false;

      if (mayHaveFileRefs) {
        occurence.init(tokenText,index1, index, WordOccurrence.Kind.FOREIGN_LANGUAGE);
        if (!processor.process(occurence)) return false;
      }
    }
    return true;
  }

  public void setMayHaveFileRefsInLiterals(final boolean mayHaveFileRefsInLiterals) {
    myMayHaveFileRefsInLiterals = mayHaveFileRefsInLiterals;
  }
}
