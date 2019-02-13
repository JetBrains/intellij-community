/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
 * <p><a name="Dependencies"><b>Dependencies</b></a></p>
 * Dependencies are objects (in particular, instances of {@link com.intellij.openapi.util.ModificationTracker}, 
 * more info - {@link com.intellij.psi.util.CachedValueProvider.Result#getDependencyItems here}), 
 * which can be tracked for changes, that should trigger folding regions recalculation for an editor (initiating code folding pass). 
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
  private final Boolean myCollapsedByDefault;
  private String myPlaceholderText;
  private boolean myCanBeRemovedWhenCollapsed;

  /**
   * Creates a folding region related to the specified AST node and covering the specified
   * text range.
   * @param node  The node to which the folding region is related. The node is then passed to
   *              {@link FoldingBuilder#getPlaceholderText(ASTNode)} and
   *              {@link FoldingBuilder#isCollapsedByDefault(ASTNode)}.
   * @param range The folded text range in file
   */
  public FoldingDescriptor(@NotNull ASTNode node, @NotNull TextRange range) {
    this(node, range, null);
  }

  public FoldingDescriptor(@NotNull PsiElement element, @NotNull TextRange range) {
    this(ObjectUtils.assertNotNull(element.getNode()), range, null);
  }

  /**
   * Creates a folding region related to the specified AST node and covering the specified
   * text range.
   * @param node  The node to which the folding region is related. The node is then passed to
   *              {@link FoldingBuilder#getPlaceholderText(ASTNode)} and
   *              {@link FoldingBuilder#isCollapsedByDefault(ASTNode)}.
   * @param range The folded text range in file
   * @param group Regions with the same group instance expand and collapse together.
   */
  public FoldingDescriptor(@NotNull ASTNode node, @NotNull TextRange range, @Nullable FoldingGroup group) {
    this(node, range, group, Collections.emptySet());
  }

  /**
   * Creates a folding region related to the specified AST node and covering the specified
   * text range.
   * @param node  The node to which the folding region is related. The node is then passed to
   *              {@link FoldingBuilder#getPlaceholderText(ASTNode)} and
   *              {@link FoldingBuilder#isCollapsedByDefault(ASTNode)}.
   * @param range The folded text range in file
   * @param group Regions with the same group instance expand and collapse together.
   * @param dependencies folding dependencies: other files or elements that could change
   * folding description, see <a href="#Dependencies">Dependencies</a>
   */
  public FoldingDescriptor(@NotNull ASTNode node, @NotNull TextRange range, @Nullable FoldingGroup group, Set<Object> dependencies) {
    this(node, range, group, dependencies, false);
  }

  /**
   * Creates a folding region related to the specified AST node and covering the specified
   * text range.
   * @param node  The node to which the folding region is related. The node is then passed to
   *              {@link FoldingBuilder#getPlaceholderText(ASTNode)} and
   *              {@link FoldingBuilder#isCollapsedByDefault(ASTNode)}.
   * @param range The folded text range in file
   * @param group Regions with the same group instance expand and collapse together.
   * @param dependencies folding dependencies: other files or elements that could change, see <a href="#Dependencies">Dependencies</a>
   * @param neverExpands shall be true for fold regions that must not be ever expanded.
   */
  public FoldingDescriptor(@NotNull ASTNode node,
                           @NotNull TextRange range,
                           @Nullable FoldingGroup group,
                           Set<Object> dependencies,
                           boolean neverExpands) {
    this(node, range, group, dependencies, neverExpands, null, null);
  }

  /**
   * Creates a folding region related to the specified AST node and covering the specified
   * text range.
   * @param e  PSI element to which the folding region is related.
   * @param start Folded text range's start offset in file
   * @param end Folded text range's end offset in file
   * @param group Regions with the same group instance expand and collapse together.
   * @param placeholderText Text displayed instead of folded text, when the region is collapsed
   */
  public FoldingDescriptor(@NotNull PsiElement e,
                           int start,
                           int end,
                           @Nullable FoldingGroup group,
                           @NotNull String placeholderText) {
    this(e.getNode(), new TextRange(start, end), group, placeholderText);
  }

  /**
   * Creates a folding region related to the specified AST node and covering the specified
   * text range.
   * @param node  The node to which the folding region is related. The node is then passed to
   *              {@link FoldingBuilder#isCollapsedByDefault(ASTNode)}.
   * @param range The folded text range in file
   * @param group Regions with the same group instance expand and collapse together.
   * @param placeholderText Text displayed instead of folded text, when the region is collapsed
   */
  public FoldingDescriptor(@NotNull ASTNode node,
                           @NotNull TextRange range,
                           @Nullable FoldingGroup group,
                           @NotNull String placeholderText) {
    this(node, range, group, Collections.emptySet(), false, placeholderText, null);
  }

  /**
   * Creates a folding region related to the specified AST node and covering the specified
   * text range.
   * @param node  The node to which the folding region is related. The node is then passed to
   *              {@link FoldingBuilder#isCollapsedByDefault(ASTNode)}.
   * @param range The folded text range in file
   * @param group Regions with the same group instance expand and collapse together.
   * @param placeholderText Text displayed instead of folded text, when the region is collapsed
   * @param collapsedByDefault Whether the region should be collapsed for newly opened files
   * @param dependencies folding dependencies: other files or elements that could change, see <a href="#Dependencies">Dependencies</a>
   */
  public FoldingDescriptor(@NotNull ASTNode node,
                           @NotNull TextRange range,
                           @Nullable FoldingGroup group,
                           @NotNull String placeholderText,
                           @Nullable("null means FoldingBuilder.isCollapsedByDefault will be used") Boolean collapsedByDefault,
                           @NotNull Set<Object> dependencies) {
    this(node, range, group, dependencies, false, placeholderText, collapsedByDefault);
  }

  /**
   * Creates a folding region related to the specified AST node and covering the specified
   * text range.
   * @param node  The node to which the folding region is related. The node is then passed to
   *              {@link FoldingBuilder#getPlaceholderText(ASTNode)} and
   *              {@link FoldingBuilder#isCollapsedByDefault(ASTNode)}.
   * @param range The folded text range in file
   * @param group Regions with the same group instance expand and collapse together.
   * @param dependencies folding dependencies: other files or elements that could change, see <a href="#Dependencies">Dependencies</a>
   * @param neverExpands shall be true for fold regions that must not be ever expanded.
   * @param placeholderText Text displayed instead of folded text, when the region is collapsed
   * @param collapsedByDefault Whether the region should be collapsed for newly opened files
   */
  public FoldingDescriptor(@NotNull ASTNode node,
                           @NotNull TextRange range,
                           @Nullable FoldingGroup group,
                           @NotNull Set<Object> dependencies,
                           boolean neverExpands,
                           @Nullable("null means FoldingBuilder.getPlaceholderText will be used") String placeholderText,
                           @Nullable("null means FoldingBuilder.isCollapsedByDefault will be used") Boolean collapsedByDefault) {
    assert range.getLength() > 0 : range + ", text: " + node.getText() + ", language = " + node.getPsi().getLanguage();
    myElement = node;
    myRange = range;
    myGroup = group;
    myDependencies = dependencies;
    assert !myDependencies.contains(null);
    myNeverExpands = neverExpands;
    myPlaceholderText = placeholderText;
    myCollapsedByDefault = collapsedByDefault;
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
    return myPlaceholderText == null ? calcPlaceholderText() : myPlaceholderText;
  }

  public void setPlaceholderText(@Nullable("null means FoldingBuilder.getPlaceholderText will be used") String placeholderText) {
    myPlaceholderText = placeholderText;
  }

  private String calcPlaceholderText() {
    PsiElement psiElement = myElement.getPsi();
    if (psiElement == null) return null;
    FoldingBuilder foldingBuilder = LanguageFolding.INSTANCE.forLanguage(psiElement.getLanguage());
    if (foldingBuilder == null) return null;
    return foldingBuilder instanceof FoldingBuilderEx
           ? ((FoldingBuilderEx)foldingBuilder).getPlaceholderText(myElement, myRange)
           : foldingBuilder.getPlaceholderText(myElement);
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

  @Nullable
  public Boolean isCollapsedByDefault() {
    return myCollapsedByDefault;
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
