// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.highlighting;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * This implementation collects specific direct children of top level elements and specific direct children of specific composite elements.
 * This covers all of the ruby PSI structure
 */
public abstract class AbstractCodeBlockSupportHandler implements CodeBlockSupportHandler {

  /**
   * @return top-level elements tokens to be handled, e.g. IF_STATEMENT, UNLESS_STATEMENT
   */
  @NotNull
  protected abstract TokenSet getTopLevelElementTypes();

  /**
   * @return direct tokens of {@code parentElementType} to be handled.
   * e.g IF_STATEMENT => [kwIF, kwUNLESS, kwEND]
   */
  @NotNull
  protected abstract TokenSet getDirectChildrenElementTypes(@Nullable IElementType parentElementType);

  /**
   * @return map of direct composite child elements to tokens to extract from them.
   * E.g.: IF_STATEMENT => {ELSE_BRANCH => kwELSE, ELSIF_BRANCH => kwELSIF, ...}
   */
  @NotNull
  protected abstract Map<IElementType, IElementType> getIndirectChildrenMap(@Nullable IElementType parentElementType);

  @NotNull
  @Override
  public List<TextRange> getCodeBlockMarkerRanges(@NotNull PsiElement elementAtCursor) {
    PsiElement parentElement = elementAtCursor.getParent();
    if (parentElement == null) {
      return Collections.emptyList();
    }

    IElementType elementType = PsiUtilCore.getElementType(elementAtCursor);
    IElementType parentElementType = PsiUtilCore.getElementType(parentElement);
    TokenSet directChildrenElementTypes = getDirectChildrenElementTypes(parentElementType);

    if (directChildrenElementTypes.contains(elementType)) {
      return computeMarkersRanges(parentElement);
    }

    PsiElement grandParentElement = parentElement.getParent();
    if (grandParentElement == null) {
      return Collections.emptyList();
    }
    Map<IElementType, IElementType> indirectChildrenMap = getIndirectChildrenMap(PsiUtilCore.getElementType(grandParentElement));

    if (elementType == indirectChildrenMap.get(parentElementType)) {
      return computeMarkersRanges(grandParentElement);
    }

    return Collections.emptyList();
  }

  @NotNull
  private List<TextRange> computeMarkersRanges(@NotNull PsiElement rootElement) {
    IElementType rootElementType = PsiUtilCore.getElementType(rootElement);
    TokenSet directChildrenTypes = getDirectChildrenElementTypes(rootElementType);
    Map<IElementType, IElementType> indirectChildrenMap = getIndirectChildrenMap(rootElementType);

    List<TextRange> result = new ArrayList<>();
    PsiElement currentElement = rootElement.getFirstChild();
    while (currentElement != null) {
      IElementType currentElementType = PsiUtilCore.getElementType(currentElement);
      if (directChildrenTypes.contains(currentElementType)) {
        result.add(currentElement.getTextRange());
      }
      else {
        if (indirectChildrenMap.containsKey(currentElementType)) {
          PsiElement firstChild = currentElement.getFirstChild();
          if (PsiUtilCore.getElementType(firstChild) == indirectChildrenMap.get(currentElementType)) {
            result.add(firstChild.getTextRange());
          }
        }
      }
      currentElement = currentElement.getNextSibling();
    }
    return result;
  }

  @NotNull
  @Override
  public TextRange getCodeBlockRange(@NotNull PsiElement elementAtCursor) {
    PsiElement run = elementAtCursor;
    while (run != null && !(run instanceof PsiFile)) {
      IElementType currentElementType = PsiUtilCore.getElementType(run);
      if (getTopLevelElementTypes().contains(currentElementType)) {
        return run.getTextRange();
      }
      IElementType parentElementType = PsiUtilCore.getElementType(run.getParent());
      if (getIndirectChildrenMap(parentElementType).containsKey(currentElementType)) {
        return run.getTextRange();
      }
      run = run.getParent();
    }
    return TextRange.EMPTY_RANGE;
  }
}
