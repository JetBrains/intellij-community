// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lexer;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This class represents the result of lexing: text and the tokens produced from it by some lexer.
 * It allows clients to inspect all tokens at once and easily move back and forward to implement some simple lexer-based checks.
 */
@ApiStatus.Experimental
public interface TokenList {

  /**
   * @return the number of tokens inside
   */
  int getTokenCount();

  /**
   * @return the full text that was split into the tokens represented here
   */
  @NotNull
  CharSequence getTokenizedText();

  /**
   * @return the start offset of the token with the given index
   */
  int getTokenStart(int index);

  /**
   * @return the end offset of the token with the given index
   */
  int getTokenEnd(int index);

  /**
   * @return the range of the token with the given index
   */
  @NotNull
  default TextRange getTokenRange(int index) {
    return new TextRange(getTokenStart(index), getTokenEnd(index));
  }

  /**
   * @return the type of the token with the given index, or null if the index is negative or exceeds token count
   */
  IElementType getTokenType(int index);

  /**
   * @return the text of the token with the given index, or null if the index is negative or exceeds token count
   */
  default CharSequence getTokenText(int index) {
    if (index < 0 || index >= getTokenCount()) return null;
    return getTokenizedText().subSequence(getTokenStart(index), getTokenEnd(index));
  }

  /**
   * @return whether {@link #getTokenType}(index) would return the given type
   */
  default boolean hasType(int index, @NotNull IElementType type) {
    return getTokenType(index) == type;
  }

  /**
   * @return whether {@link #getTokenType}(index) would return any of the given types (null acceptable, indicating start or end of the text)
   */
  default boolean hasType(int index, @Nullable IElementType @NotNull ... types) {
    return ArrayUtil.contains(getTokenType(index), types);
  }

  /**
   * @return whether {@link #getTokenType}(index) would return a type in the given set
   */
  default boolean hasType(int index, @NotNull TokenSet types) {
    return types.contains(getTokenType(index));
  }

  /**
   * Moves back, potentially skipping tokens which represent a valid nesting sequence
   * with the given types for opening and closing braces.
   * @return an index {@code prev} of a token before {@code index} such that either:
   * <ol>
   *   <li>{@code prev == index - 1}</li>
   *   <li>{@code hasType(prev + 1, opening) && hasType(index, closing)} and every opening brace between those indices has its closing one before {@code index}</li>
   * </ol>
   */
  default int backWithBraceMatching(int index, @NotNull IElementType opening, @NotNull IElementType closing) {
    if (getTokenType(index) == closing) {
      int nesting = 1;
      while (nesting > 0 && index > 0) {
        index--;
        IElementType type = getTokenType(index);
        if (type == closing) {
          nesting++;
        }
        else if (type == opening) {
          nesting--;
        }
      }
    }
    return index - 1;
  }

  /**
   * Moves back from {@code index} while tokens belong to the given set
   * @return the largest {@code prev <= index} whose token type doesn't belong to {@code toSkip}
   */
  default int backWhile(int index, @NotNull TokenSet toSkip) {
    while (hasType(index, toSkip)) {
      index--;
    }
    return index;
  }

  /**
   * Moves forward from {@code index} while tokens belong to the given set
   * @return the smallest {@code next >= index} whose token type doesn't belong to {@code toSkip}
   */
  default int forwardWhile(int index, @NotNull TokenSet toSkip) {
    while (hasType(index, toSkip)) {
      index++;
    }
    return index;
  }

  /**
   * @return a view of this object as a {@link Lexer}.
   * Note that the returned lexer isn't the same as the one that produced this tokenized text: it returns the same offsets and types,
   * but states and positions might differ. The returned lexer may be used to avoid tokenizing the same text again in APIs where lexer is expected,
   * but it will only accept the very same text from the very beginning; it can't be used on any other strings.
   */
  default @NotNull Lexer asLexer() {
    return new WrappingLexer(this);
  }

  /**
   * A simple lexer over {@link TokenList}.
   */
  class WrappingLexer extends LexerBase {
    private final TokenList myTokens;
    private int myIndex;

    WrappingLexer(TokenList tokens) {
      this.myTokens = tokens;
    }

    public TokenList getTokens() {
      return myTokens;
    }

    @Override
    public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
      assert Comparing.equal(buffer, myTokens.getTokenizedText());
      assert startOffset == 0;
      assert endOffset == buffer.length();
      assert initialState == 0;
      myIndex = 0;
    }

    @Override
    public int getState() {
      return myIndex;
    }

    @Override
    public @Nullable IElementType getTokenType() {
      return myTokens.getTokenType(myIndex);
    }

    @Override
    public int getTokenStart() {
      return myTokens.getTokenStart(myIndex);
    }

    @Override
    public int getTokenEnd() {
      return myTokens.getTokenEnd(myIndex);
    }

    @Override
    public void advance() {
      myIndex++;
    }

    @Override
    public @NotNull CharSequence getBufferSequence() {
      return myTokens.getTokenizedText();
    }

    @Override
    public int getBufferEnd() {
      return myTokens.getTokenizedText().length();
    }
  }

}
