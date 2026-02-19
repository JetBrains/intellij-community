// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.tree;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

/**
 * An additional interface to be implemented by {@link IElementType} instances for tokens which should be parsed lazily, but 
 * for some reason extending {@link ILazyParseableElementType} is impossible.
 */
public interface ILazyParseableElementTypeBase {

  /**
   * Parses the contents of the specified chameleon node and returns the AST tree
   * representing the parsed contents.
   *
   * @param chameleon the node to parse.
   * @return the parsed contents of the node.
   */
  ASTNode parseContents(@NotNull ASTNode chameleon);

  /**
   * @return whether it's safe during lazy parsing to reuse tokens that were previously collapsed into a single token of this
   * type via {@link com.intellij.lang.PsiBuilder.Marker#collapse}. This can increase lazy-parsing speed
   * (by not having to run the lexer again), but requires additional memory to store the tokens.
   */
  default boolean reuseCollapsedTokens() {
    return false;
  }
}
