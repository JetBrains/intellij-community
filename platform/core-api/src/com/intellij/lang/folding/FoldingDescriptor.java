/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.editor.FoldingGroup;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;

/**
 * Defines a single folding region in the code.
 *
 * @author max
 * @see FoldingBuilder
 */
public class FoldingDescriptor {
  public static final FoldingDescriptor[] EMPTY = new FoldingDescriptor[0];

  private final ASTNode myElement;
  private final TextRange myRange;
  @Nullable private final FoldingGroup myGroup;
  private final Set<Object> myDependencies;
  private final boolean myNeverExpands;
  private boolean myCanBeRemovedWhenCollapsed;

  /**
   * Creates a folding region related to the specified AST node and covering the specified
   * text range.
   * @param node  The node to which the folding region is related. The node is then passed to
   *              {@link FoldingBuilder#getPlaceholderText(com.intellij.lang.ASTNode)} and
   *              {@link FoldingBuilder#isCollapsedByDefault(com.intellij.lang.ASTNode)}.
   * @param range The folded text range.
   */
  public FoldingDescriptor(@NotNull ASTNode node, @NotNull TextRange range) {
    this(node, range, null);
  }

  public FoldingDescriptor(@NotNull PsiElement element, @NotNull TextRange range) {
    this(ObjectUtils.assertNotNull(element.getNode()), range, null);
  }

  public FoldingDescriptor(@NotNull ASTNode node, @NotNull TextRange range, @Nullable FoldingGroup group) {
    this(node, range, group, Collections.<Object>emptySet());
  }

  /**
   * Creates a folding region related to the specified AST node and covering the specified
   * text range.
   * @param node  The node to which the folding region is related. The node is then passed to
   *              {@link com.intellij.lang.folding.FoldingBuilder#getPlaceholderText(com.intellij.lang.ASTNode)} and
   *              {@link com.intellij.lang.folding.FoldingBuilder#isCollapsedByDefault(com.intellij.lang.ASTNode)}.
   * @param range The folded text range.
   * @param group Regions with the same group instance expand and collapse together.
   * @param dependencies folding dependencies: other files or elements that could change
   * folding description
   */
  public FoldingDescriptor(@NotNull ASTNode node, @NotNull TextRange range, @Nullable FoldingGroup group, Set<Object> dependencies) {
    this(node, range, group, dependencies, false);
  }

  /**
   * Creates a folding region related to the specified AST node and covering the specified
   * text range.
   * @param node  The node to which the folding region is related. The node is then passed to
   *              {@link com.intellij.lang.folding.FoldingBuilder#getPlaceholderText(com.intellij.lang.ASTNode)} and
   *              {@link com.intellij.lang.folding.FoldingBuilder#isCollapsedByDefault(com.intellij.lang.ASTNode)}.
   * @param range The folded text range.
   * @param group Regions with the same group instance expand and collapse together.
   * @param dependencies folding dependencies: other files or elements that could change
   * @param neverExpands shall be true for fold regions that must not be ever expanded.
   */
  public FoldingDescriptor(@NotNull ASTNode node,
                           @NotNull TextRange range,
                           @Nullable FoldingGroup group,
                           Set<Object> dependencies,
                           boolean neverExpands) {
    assert range.getLength() > 0 : range + ", text: " + node.getText() + ", language = " + node.getPsi().getLanguage();
    myElement = node;
    myRange = range;
    myGroup = group;
    myDependencies = dependencies;
    assert !myDependencies.contains(null);
    myNeverExpands = neverExpands;
  }

  /**
   * @return the node to which the folding region is related.
   */
  @NotNull
  public ASTNode getElement() {
    return myElement;
  }

  /**
   * Returns the folded text range.
   * @return the folded text range.
   */
  @NotNull
  public TextRange getRange() {
    return myRange;
  }

  @Nullable
  public FoldingGroup getGroup() {
    return myGroup;
  }

  @Nullable
  public String getPlaceholderText() {
    final PsiElement psiElement = myElement.getPsi();
    if (psiElement == null) return null;

    final Language lang = psiElement.getLanguage();
    final FoldingBuilder foldingBuilder = LanguageFolding.INSTANCE.forLanguage(lang);
    if (foldingBuilder != null) {
      return foldingBuilder instanceof FoldingBuilderEx
             ? ((FoldingBuilderEx)foldingBuilder).getPlaceholderText(myElement, myRange)
             : foldingBuilder.getPlaceholderText(myElement);
    }
    return null;
  }

  @NotNull
  public Set<Object> getDependencies() {
    return myDependencies;
  }

  public boolean isNonExpandable() {
    return myNeverExpands;
  }

  public boolean canBeRemovedWhenCollapsed() {
    return myCanBeRemovedWhenCollapsed;
  }

  /**
   * By default, collapsed regions are not removed automatically, even if related PSI elements become invalid.
   * This method allows to override default behaviour for specific regions.
   */
  public void setCanBeRemovedWhenCollapsed(boolean canBeRemovedWhenCollapsed) {
    myCanBeRemovedWhenCollapsed = canBeRemovedWhenCollapsed;
  }

  @SuppressWarnings("HardCodedStringLiteral")
  @Override
  public String toString() {
    return myRange + " for AST: " + myElement;
  }
}
