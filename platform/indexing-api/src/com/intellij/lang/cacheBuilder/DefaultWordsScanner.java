/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.diagnostic.PluginException;
import com.intellij.lexer.DelegateLexer;
import com.intellij.lexer.FlexAdapter;
import com.intellij.lexer.Lexer;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

/**
 * The default implementation of a words scanner based on a custom language lexer.
 *
 * @author max
 */

public class DefaultWordsScanner extends VersionedWordsScanner {
  private final Lexer myLexer;
  private final TokenSet myIdentifierTokenSet;
  private final TokenSet myCommentTokenSet;
  private final TokenSet myLiteralTokenSet;
  private final TokenSet mySkipCodeContextTokenSet;
  private final TokenSet myProcessAsWordTokenSet;
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
    this(lexer, identifierTokenSet, commentTokenSet, literalTokenSet, TokenSet.EMPTY);
  }

  /**
   * Creates a new instance of the words scanner.
   *
   * @param lexer              the lexer used for breaking the text into tokens.
   * @param identifierTokenSet the set of token types which represent identifiers.
   * @param commentTokenSet    the set of token types which represent comments.
   * @param literalTokenSet    the set of token types which represent literals.
   * @param skipCodeContextTokenSet the set of token types which should not be considered as code context.
   */
  public DefaultWordsScanner(final Lexer lexer, final TokenSet identifierTokenSet, final TokenSet commentTokenSet,
                             final TokenSet literalTokenSet, final @NotNull TokenSet skipCodeContextTokenSet) {
    this(lexer, identifierTokenSet, commentTokenSet, literalTokenSet, skipCodeContextTokenSet, TokenSet.EMPTY);
  }

  /**
   * Creates a new instance of the words scanner.
   *
   * @param lexer              the lexer used for breaking the text into tokens.
   * @param identifierTokenSet the set of token types which represent identifiers.
   * @param commentTokenSet    the set of token types which represent comments.
   * @param literalTokenSet    the set of token types which represent literals.
   * @param skipCodeContextTokenSet the set of token types which should not be considered as code context.
   * @param processAsWordTokenSet   the set of token types which represent overload operators.
   */
  public DefaultWordsScanner(final Lexer lexer, final TokenSet identifierTokenSet, final TokenSet commentTokenSet,
                             final TokenSet literalTokenSet, @NotNull TokenSet skipCodeContextTokenSet,
                             final @NotNull TokenSet processAsWordTokenSet) {
    myLexer = lexer;
    myIdentifierTokenSet = identifierTokenSet;
    myCommentTokenSet = commentTokenSet;
    myLiteralTokenSet = literalTokenSet;
    mySkipCodeContextTokenSet = skipCodeContextTokenSet;
    myProcessAsWordTokenSet = processAsWordTokenSet;
  }

  private volatile boolean myBusy;

  @Override
  public void processWords(@NotNull CharSequence fileText, @NotNull Processor<? super WordOccurrence> processor) {
    if (myBusy) {
      throw PluginException.createByClass("Different word scanner instances should be used for different threads, " +
                                          "make sure that " + this + " with " + myLexer + " is instantiated on every request and not shared",
                                          null,
                                          guessPluginClass());
    }
    myBusy = true;
    try {
      myLexer.start(fileText);
      WordOccurrence occurrence = new WordOccurrence(fileText, 0, 0, null); // shared occurrence

      IElementType type;
      while ((type = myLexer.getTokenType()) != null) {
        if (myProcessAsWordTokenSet.contains(type)) {
          occurrence.init(fileText, myLexer.getTokenStart(),myLexer.getTokenEnd(), WordOccurrence.Kind.CODE);
          processor.process(occurrence);
        }
        else if (myIdentifierTokenSet.contains(type)) {
          //occurrence.init(fileText, myLexer.getTokenStart(), myLexer.getTokenEnd(), WordOccurrence.Kind.CODE);
          //if (!processor.process(occurrence)) return;
          if (!stripWords(processor, fileText, myLexer.getTokenStart(), myLexer.getTokenEnd(), WordOccurrence.Kind.CODE, occurrence, false)) return;      }
        else if (myCommentTokenSet.contains(type)) {
          if (!stripWords(processor, fileText,myLexer.getTokenStart(),myLexer.getTokenEnd(), WordOccurrence.Kind.COMMENTS,occurrence, false)) return;
        }
        else if (myLiteralTokenSet.contains(type)) {
          if (!stripWords(processor, fileText, myLexer.getTokenStart(),myLexer.getTokenEnd(),WordOccurrence.Kind.LITERALS,occurrence, myMayHaveFileRefsInLiterals)) return;
        }
        else if (!mySkipCodeContextTokenSet.contains(type)) {
          if (!stripWords(processor, fileText, myLexer.getTokenStart(), myLexer.getTokenEnd(), WordOccurrence.Kind.CODE, occurrence, false)) return;
        }
        myLexer.advance();
      }
    }
    finally {
      myBusy = false;
    }
  }

  private Class<?> guessPluginClass() {
    if (myIdentifierTokenSet.getTypes().length > 0) {
      return myIdentifierTokenSet.getTypes()[0].getClass();
    }
    if (myLiteralTokenSet.getTypes().length > 0) {
      return myLiteralTokenSet.getTypes()[0].getClass();
    }
    Object lexer = myLexer;
    while (true) {
      if (lexer instanceof FlexAdapter) {
        lexer = ((FlexAdapter)lexer).getFlex();
      }
      else if (lexer instanceof DelegateLexer) {
        lexer = ((DelegateLexer)lexer).getDelegate();
      }
      else {
        break;
      }
    }
    return lexer.getClass();
  }

  public static boolean stripWords(final Processor<? super WordOccurrence> processor,
                                   final CharSequence tokenText,
                                   int from,
                                   int to,
                                   final WordOccurrence.Kind kind,
                                   @NotNull WordOccurrence occurrence,
                                   boolean mayHaveFileRefs
  ) {
    // This code seems strange but it is more effective as Character.isJavaIdentifier_xxx_ is quite costly operation due to unicode
    int index = from;

    ScanWordsLoop:
    while (true) {
      while (true) {
        if (index == to) break ScanWordsLoop;
        char c = tokenText.charAt(index);
        if (isAsciiIdentifierPart(c) || Character.isJavaIdentifierStart(c)) {
          break;
        }
        index++;
      }
      int wordStart = index;
      while (true) {
        index++;
        if (index == to) break;
        char c = tokenText.charAt(index);
        if (isAsciiIdentifierPart(c)) continue;
        if (!Character.isJavaIdentifierPart(c)) break;
      }
      int wordEnd = index;
      occurrence.init(tokenText, wordStart, wordEnd, kind);

      if (!processor.process(occurrence)) return false;

      if (mayHaveFileRefs) {
        occurrence.init(tokenText, wordStart, wordEnd, WordOccurrence.Kind.FOREIGN_LANGUAGE);
        if (!processor.process(occurrence)) return false;
      }
    }
    return true;
  }

  private static boolean isAsciiIdentifierPart(char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '$';
  }

  public void setMayHaveFileRefsInLiterals(final boolean mayHaveFileRefsInLiterals) {
    myMayHaveFileRefsInLiterals = mayHaveFileRefsInLiterals;
  }
}
