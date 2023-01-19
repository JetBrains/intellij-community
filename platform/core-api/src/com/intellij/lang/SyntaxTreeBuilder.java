// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface SyntaxTreeBuilder {
  /**
   * Returns the complete text being parsed.
   *
   * @return the text being parsed
   */
  @NotNull
  CharSequence getOriginalText();

  /**
   * Advances the lexer to the next token, skipping whitespace and comment tokens.
   */
  void advanceLexer();

  /**
   * Returns the type of current token from the lexer.
   *
   * @return the token type, or {@code null} when the token stream is over.
   * @see #setTokenTypeRemapper(ITokenTypeRemapper).
   */
  @Nullable
  IElementType getTokenType();

  /**
   * Sets optional remapper that can change the type of tokens.
   * Output of {@link #getTokenType()} is affected by it.
   *
   * @param remapper the remapper object, or {@code null}.
   */
  void setTokenTypeRemapper(@Nullable ITokenTypeRemapper remapper);

  /**
   * Slightly easier way to what {@link ITokenTypeRemapper} does (i.e. it just remaps current token to a given type).
   *
   * @param type new type for the current token.
   */
  void remapCurrentToken(@NotNull IElementType type);

  /**
   * Subscribe for notification on default whitespace and comments skipped events.
   *
   * @param callback an implementation for the callback
   */
  void setWhitespaceSkippedCallback(@Nullable WhitespaceSkippedCallback callback);

  /**
   * See what token type is in {@code steps} ahead.
   *
   * @param steps 0 is current token (i.e., the same {@link PsiBuilder#getTokenType()} returns)
   * @return type element which {@link #getTokenType()} will return if we call advance {@code steps} times in a row
   */
  @Nullable
  IElementType lookAhead(int steps);

  /**
   * See what token type is in {@code steps} ahead/behind.
   *
   * @param steps 0 is current token (i.e., the same {@link PsiBuilder#getTokenType()} returns)
   * @return type element ahead or behind, including whitespace/comment tokens
   */
  @Nullable
  IElementType rawLookup(int steps);

  /**
   * See what token type is in {@code steps} ahead/behind current position.
   *
   * @param steps 0 is current token (i.e., the same {@link #getTokenType()} returns)
   * @return offset type element ahead or behind, including whitespace/comment tokens, -1 if first token,
   * {@code getOriginalText().getLength()} at end
   */
  int rawTokenTypeStart(int steps);

  /**
   * Returns the index of the current token in the original sequence.
   *
   * @return token index
   */
  int rawTokenIndex();

  /**
   * Returns the text of the current token from the lexer.
   *
   * @return the token text, or {@code null} when the token stream is over.
   */
  @NonNls
  @Nullable
  String getTokenText();

  /**
   * Returns the start offset of the current token, or the file length when the token stream is over.
   *
   * @return the token offset.
   */
  int getCurrentOffset();

  /**
   * Creates a marker at the current parsing position.
   *
   * @return the new marker instance.
   */
  @NotNull
  Marker mark();

  /**
   * Adds an error marker with the specified message text at the current position in the tree.
   * <br><b>Note</b>: from series of subsequent errors messages only first will be part of resulting tree.
   *
   * @param messageText the text of the error message displayed to the user.
   */
  void error(@NotNull @NlsContexts.ParsingError String messageText);

  /**
   * Checks if the lexer has reached the end of file.
   *
   * @return {@code true} if the lexer is at end of file, {@code false} otherwise.
   */
  boolean eof();

  /**
   * Enables or disables the builder debug mode. In debug mode, the builder will print stack trace
   * to marker allocation position if one is not done when calling {@link PsiBuilder#getTreeBuilt()}.
   *
   * @param dbgMode the debug mode value.
   */
  void setDebugMode(boolean dbgMode);

  void enforceCommentTokens(@NotNull TokenSet tokens);

  /**
   * @return latest left done node for context dependent parsing.
   */
  @Nullable
  LighterASTNode getLatestDoneMarker();

  default boolean isWhitespaceOrComment(@NotNull IElementType elementType) {
    return false;
  }

  /**
   * A marker defines a range in the document text which becomes a node in the AST
   * tree. The ranges defined by markers within the text range of the current marker
   * become child nodes of the node defined by the current marker.
   */
  interface Marker {
    /**
     * Creates and returns a new marker starting immediately before the start of
     * this marker and extending after its end. Can be called on a completed or
     * a currently active marker.
     *
     * @return the new marker instance.
     */
    @NotNull
    Marker precede();

    /**
     * Drops this marker. Can be called after other markers have been added and completed
     * after this marker. Does not affect lexer position or markers added after this marker.
     */
    void drop();

    /**
     * Drops this marker and all markers added after it, and reverts the lexer position to the
     * position of this marker.
     */
    void rollbackTo();

    /**
     * Completes this marker and labels it with the specified AST node type. Before calling this method,
     * all markers added after the beginning of this marker must be either dropped or completed.
     *
     * @param type the type of the node in the AST tree.
     */
    void done(@NotNull IElementType type);

    /**
     * Like {@linkplain #done(IElementType)}, but collapses all tokens between start and end markers
     * into single leaf node of given type.
     *
     * @param type the type of the node in the AST tree.
     */
    void collapse(@NotNull IElementType type);

    /**
     * Like {@linkplain #done(IElementType)}, but the marker is completed (end marker inserted)
     * before specified one. All markers added between start of this marker and the marker specified as end one
     * must be either dropped or completed.
     *
     * @param type   the type of the node in the AST tree.
     * @param before marker to complete this one before.
     */
    void doneBefore(@NotNull IElementType type, @NotNull Marker before);

    /**
     * Like {@linkplain #doneBefore(IElementType, Marker)}, but in addition an error element with given text
     * is inserted right before this marker's end.
     *
     * @param type         the type of the node in the AST tree.
     * @param before       marker to complete this one before.
     * @param errorMessage for error element.
     */
    void doneBefore(@NotNull IElementType type, @NotNull Marker before, @NotNull @NlsContexts.ParsingError String errorMessage);

    /**
     * Completes this marker and labels it as error element with specified message. Before calling this method,
     * all markers added after the beginning of this marker must be either dropped or completed.
     *
     * @param message for error element.
     */
    void error(@NotNull @NlsContexts.ParsingError String message);

    /**
     * Like {@linkplain #error(String)}, but the marker is completed before specified one.
     *
     * @param message for error element.
     * @param before  marker to complete this one before.
     */
    void errorBefore(@NotNull @NlsContexts.ParsingError String message, @NotNull Marker before);

    /**
     * Allows to define custom edge token binders instead of default ones. If any of parameters is null
     * then corresponding token binder won't be changed (keeping previously set or default token binder).
     * It is an error to set right token binder for not-done marker.
     *
     * @param left  new left edge token binder.
     * @param right new right edge token binder.
     */
    void setCustomEdgeTokenBinders(@Nullable WhitespacesAndCommentsBinder left, @Nullable WhitespacesAndCommentsBinder right);
  }
}
