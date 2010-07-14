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
package com.intellij.lang.folding;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.FoldingGroup;
import com.intellij.openapi.util.ProperTextRange;
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
 * @author Konstantin Bulenkov
 * @see FoldingBuilder
 */                                                    
public class FoldingDescriptor {
  public static final FoldingDescriptor[] EMPTY = new FoldingDescriptor[0];

  private final ASTNode myElement;
  private final TextRange myRange;
  @Nullable private final FoldingGroup myGroup;
  private Set<Object> myDependencies;
  private String myText;

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
   *              {@link FoldingBuilder#getPlaceholderText(com.intellij.lang.ASTNode)} and
   *              {@link FoldingBuilder#isCollapsedByDefault(com.intellij.lang.ASTNode)}.
   * @param range The folded text range.
   * @param group Regions with the same group instance expand and collapse together.
   * @param dependencies folding dependencies: other files or elements that could change
   * folding description
   */
  public FoldingDescriptor(@NotNull ASTNode node, @NotNull TextRange range, @Nullable FoldingGroup group, Set<Object> dependencies) {
    assert range.getStartOffset() + 1 < range.getEndOffset() : range;
    myElement = node;
    ProperTextRange.assertProperRange(range);
    myRange = range;
    myGroup = group;
    assert getRange().getLength() >= 2 : "range:" + getRange();
    myDependencies = dependencies;
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
    if (myText == null) {
      final FoldingBuilder builder = getFoldingBuilder();
      return builder == null ? null : builder.getPlaceholderText(myElement);
    } else {
      return myText;
    }
  }

  @Nullable
  protected FoldingBuilder getFoldingBuilder() {
    final PsiElement psi = myElement.getPsi();
    if (psi == null) return null;

    final Language lang = psi.getLanguage();
    return LanguageFolding.INSTANCE.forLanguage(lang);
  }

  public boolean isCollapsedByDefault() {
    final FoldingBuilder builder = getFoldingBuilder();
    return builder == null ? false : builder.isCollapsedByDefault(myElement);
  }
  
  @NotNull
  public Set<Object> getDependencies() {
    return myDependencies;
  }

  public String getText() {
    return myText;
  }

  public void setText(String text) {
    myText = text;
  }
}
