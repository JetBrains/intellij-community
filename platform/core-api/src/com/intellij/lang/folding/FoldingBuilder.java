// Copyright 2000-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.folding;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.PossiblyDumbAware;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows a custom language plugin to define rules for folding code in the language handled
 * by the plugin.
 *
 * @author max
 * @see LanguageFolding#forLanguage(Language)
 */

public interface FoldingBuilder extends PossiblyDumbAware {
  /**
   * Builds the folding regions for the specified node in the AST tree and its children.
   * Note that you can have several folding regions for one AST node, i.e. several {@link FoldingDescriptor} with similar AST node.
   *
   * @param node     the node for which folding is requested.
   * @param document the document for which folding is built. Can be used to retrieve line
   *                 numbers for folding regions.
   * @return the array of folding descriptors.
   */
  FoldingDescriptor @NotNull [] buildFoldRegions(@NotNull ASTNode node, @NotNull Document document);

  /**
   * Returns the text which is displayed in the editor for the folding region related to the
   * specified node when the folding region is collapsed.
   *
   * @param node the node for which the placeholder text is requested.
   * @return the placeholder text.
   */
  @Nullable
  String getPlaceholderText(@NotNull ASTNode node);

  /**
   * Returns the default collapsed state for the folding region related to the specified node.
   * <p/>
   * The expand-all action applies a two-step behavior that - in step one - expands all regions except those
   * that return {@code true} here. In step two, all regions are expanded.
   *
   * @param node the node for which the collapsed state is requested.
   * @return true if the region is collapsed by default, false otherwise.
   */
  boolean isCollapsedByDefault(@NotNull ASTNode node);

  default boolean isCollapsedByDefault(@NotNull FoldingDescriptor foldingDescriptor) {
    return isCollapsedByDefault(foldingDescriptor.getElement());
  }

  /**
   * Returns the behavior of the folding region when performing the collapse-all action.
   * <p/>
   * The collapse-all action applies a two-step behavior that - in step one - collapses all regions except those
   * that return {@code true} here. In step two, all regions are collapsed.
   *
   * @param node the node for which the expanded state is requested.
   * @return true if the region is not collapsed when the collapse-all action is applied the first time.
   */
  default boolean keepExpandedOnFirstCollapseAll(@NotNull ASTNode node) {
    return false;
  }

  default boolean keepExpandedOnFirstCollapseAll(@NotNull FoldingDescriptor foldingDescriptor) {
    return keepExpandedOnFirstCollapseAll(foldingDescriptor.getElement());
  }
}
