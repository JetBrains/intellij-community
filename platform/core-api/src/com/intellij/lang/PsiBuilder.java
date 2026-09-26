// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.diff.FlyweightCapableTreeStructure;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The IDE side of a custom language parser. Provides lexical analysis results to the
 * plugin and allows the plugin to build the AST tree.
 *
 * @see PsiParser
 * @see ASTNode
 */
public interface PsiBuilder extends SyntaxTreeBuilder, UserDataHolder {
  /**
   * Returns a project for which PSI builder was created (see {@link PsiBuilderFactory}).
   *
   * @return project.
   */
  Project getProject();

  /**
   * Returns the result of the parsing. All markers must be completed or dropped before this method is called.
   *
   * @return the built tree.
   */
  @NotNull
  ASTNode getTreeBuilt();

  /**
   * Same as {@link #getTreeBuilt()} but returns a light tree, which is build faster,
   * produces less garbage but is incapable of creating a PSI over.
   * <br><b>Note</b>: this method shouldn't be called if {@link #getTreeBuilt()} was called before.
   *
   * @return the light tree built.
   */
  @NotNull
  FlyweightCapableTreeStructure<LighterASTNode> getLightTree();

  @Override
  @NotNull Marker mark();

  /**
   * @deprecated use {@link #getUserData} instead
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @ApiStatus.ScheduledForRemoval
  @Deprecated
  default @Nullable <T> T getUserDataUnprotected(@NotNull Key<T> key) {
    return getUserData(key);
  }

  /**
   * @deprecated use {@link #putUserData} instead
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @ApiStatus.ScheduledForRemoval
  @Deprecated
  default <T> void putUserDataUnprotected(@NotNull Key<T> key, @Nullable T value) {
    putUserData(key, value);
  }

  interface Marker extends SyntaxTreeBuilder.Marker {
    @Override
    @NotNull
    Marker precede();

    @Override
    default void doneBefore(@NotNull IElementType type, SyntaxTreeBuilder.@NotNull Marker before) {
      doneBefore(type, (Marker)before);
    }

    @Override
    default void doneBefore(@NotNull IElementType type,
                            SyntaxTreeBuilder.@NotNull Marker before,
                            @NotNull @NlsContexts.ParsingError String errorMessage) {
      doneBefore(type, (Marker) before, errorMessage);
    }

    @Override
    default void errorBefore(@NotNull @NlsContexts.ParsingError String message, SyntaxTreeBuilder.@NotNull Marker before) {
      errorBefore(message, (Marker) before);
    }

    void doneBefore(@NotNull IElementType type, @NotNull Marker before);

    void doneBefore(@NotNull IElementType type,
                            @NotNull Marker before,
                            @NotNull @NlsContexts.ParsingError String errorMessage);

    void errorBefore(@NotNull @NlsContexts.ParsingError String message, @NotNull Marker before);
  }
}
