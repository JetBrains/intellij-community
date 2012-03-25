/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.lang.folding;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows a custom language plugin to define rules for folding code in the language handled
 * by the plugin.
 *
 * @author max
 * @see LanguageFolding#forLanguage(Language)
 */

public interface FoldingBuilder {
  /**
   * Builds the folding regions for the specified node in the AST tree and its children.
   * Note that you can have several folding regions for one AST node, i.e. several {@link FoldingDescriptor} with similar AST node.
   *
   * @param node     the node for which folding is requested.
   * @param document the document for which folding is built. Can be used to retrieve line
   *                 numbers for folding regions.
   * @return the array of folding descriptors.
   */
  @NotNull
  FoldingDescriptor[] buildFoldRegions(@NotNull ASTNode node, @NotNull Document document);

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
   *
   * @param node the node for which the collapsed state is requested.
   * @return true if the region is collapsed by default, false otherwise.
   */
  boolean isCollapsedByDefault(@NotNull ASTNode node);
}
