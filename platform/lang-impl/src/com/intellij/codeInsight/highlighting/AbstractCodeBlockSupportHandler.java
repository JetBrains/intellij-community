// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.highlighting;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This implementation collects specific direct children of top level elements and specific direct children of specific composite elements.
 */
public abstract class AbstractCodeBlockSupportHandler implements CodeBlockSupportHandler {
  /**
   * @return All element types which don't have a parent, e.g. {@code IF_STATEMENT}.
   * {@link #getDirectChildrenElementTypes} should never return these element types
   */
  protected abstract @NotNull TokenSet getTopLevelElementTypes();

  /**
   * @return Elements which should be highlighted. These elements don't have children.
   * {@link #getDirectChildrenElementTypes} should return an empty TokenSet for an element type if it is a keyword.
   */
  protected abstract @NotNull TokenSet getKeywordElementTypes();

  /**
   * @return Block element types which should be used for navigation.
   */
  protected abstract @NotNull TokenSet getBlockElementTypes();

  /**
   * The method defines a highlighting tree.
   * Highlighting tree nodes are PSI elements.
   * A highlighting tree has an edge from A to B if and only if
   * <ul>
   * <li>A is a parent for B in the PSI tree</li>
   * <li>{@code getDirectChildrenElementTypes(getElementType(A)).contains(getElementType(B))}</li>
   * </ul>
   * If two keyword elements have a common ancestor in a highlighting tree they will be highlighted together.
   * The method should be consistent with {@link #getTopLevelElementTypes()} and {@link #getKeywordElementTypes()}.
   * <p/>
   * For example, for the given if statement:
   * <br>
   * <pre>
   * if expr
   * elsif expr
   * else
   * end
   * </pre>
   * the children should be defined as follows:
   * <pre>
   * IF_STATEMENT => {kIF, ELSIF_BLOCK, ELSE_BLOCK, kEND}
   * ELSIF_BLOCK => {kELSEIF}
   * ELSE_BLOCK => {kELSE}
   * </pre>
   */
  protected abstract @NotNull TokenSet getDirectChildrenElementTypes(@Nullable IElementType parentElementType);

  @Override
  public @NotNull List<TextRange> getCodeBlockMarkerRanges(@NotNull PsiElement elementAtCursor) {
    TokenSet keywordElementTypes = getKeywordElementTypes();
    if (!keywordElementTypes.contains(PsiUtilCore.getElementType(elementAtCursor))) {
      return Collections.emptyList();
    }
    final PsiElement rootElement = getParentByTokenSet(elementAtCursor, getTopLevelElementTypes());
    if (rootElement == null) {
      return Collections.emptyList();
    }
    return computeMarkersRanges(rootElement, keywordElementTypes);
  }

  @Override
  public @NotNull TextRange getCodeBlockRange(@NotNull PsiElement elementAtCursor) {
    PsiElement obj = getParentByTokenSet(elementAtCursor, getBlockElementTypes());
    TextRange value = obj == null ? null : obj.getTextRange();
    return value == null ? TextRange.EMPTY_RANGE : value;
  }

  private @NotNull List<TextRange> computeMarkersRanges(@NotNull PsiElement rootElement, @NotNull TokenSet keywordsElementTypes) {
    final IElementType type = PsiUtilCore.getElementType(rootElement);
    if (keywordsElementTypes.contains(type)) {
      return Collections.singletonList(rootElement.getTextRange());
    }
    TokenSet directChildrenTypes = getDirectChildrenElementTypes(type);
    List<TextRange> result = new ArrayList<>();
    PsiElement currentElement = rootElement.getFirstChild();
    while (currentElement != null) {
      IElementType currentElementType = PsiUtilCore.getElementType(currentElement);
      if (directChildrenTypes.contains(currentElementType)) {
        result.addAll(computeMarkersRanges(currentElement, keywordsElementTypes));
      }
      currentElement = currentElement.getNextSibling();
    }
    return result;
  }

  private static @Nullable PsiElement getParentByTokenSet(@NotNull PsiElement element, @NotNull TokenSet tokenSet) {
    PsiElement run = element;
    while (run != null && !tokenSet.contains(PsiUtilCore.getElementType(run))) {
      run = run.getParent();
    }
    return run;
  }
}
