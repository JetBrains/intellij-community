// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.Stack;
import gnu.trove.TIntStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class CollectHighlightsUtil {
  private static final ExtensionPointName<Condition<PsiElement>> EP_NAME = ExtensionPointName.create("com.intellij.elementsToHighlightFilter");

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.CollectHighlightsUtil");

  private CollectHighlightsUtil() { }

  @NotNull
  public static List<PsiElement> getElementsInRange(@NotNull PsiElement root, final int startOffset, final int endOffset) {
    return getElementsInRange(root, startOffset, endOffset, false);
  }

  @NotNull
  public static List<PsiElement> getElementsInRange(@NotNull PsiElement root,
                                                    final int startOffset,
                                                    final int endOffset,
                                                    boolean includeAllParents) {
    PsiElement commonParent = findCommonParent(root, startOffset, endOffset);
    if (commonParent == null) return new ArrayList<>();
    final List<PsiElement> list = getElementsToHighlight(commonParent, startOffset, endOffset);

    PsiElement parent = commonParent;
    while (parent != null && parent != root) {
      list.add(parent);
      parent = includeAllParents ? parent.getParent() : null;
    }

    list.add(root);

    return list;
  }

  private static final int STARTING_TREE_HEIGHT = 100;

  static class FiltersHolder {
    static final Condition<PsiElement>[] FILTERS = EP_NAME.getExtensions();
  }
  @NotNull
  private static List<PsiElement> getElementsToHighlight(@NotNull PsiElement parent, final int startOffset, final int endOffset) {
    final List<PsiElement> result = new ArrayList<>();
    final int currentOffset = parent.getTextRange().getStartOffset();
    final Condition<PsiElement>[] filters = FiltersHolder.FILTERS;

    int offset = currentOffset;

    final TIntStack starts = new TIntStack(STARTING_TREE_HEIGHT);
    final Stack<PsiElement> elements = new Stack<>(STARTING_TREE_HEIGHT);
    final Stack<PsiElement> children = new Stack<>(STARTING_TREE_HEIGHT);
    PsiElement element = parent;

    PsiElement child = PsiUtilCore.NULL_PSI_ELEMENT;
    while (true) {
      ProgressIndicatorProvider.checkCanceled();

      for (Condition<PsiElement> filter : filters) {
        if (!filter.value(element)) {
          assert child == PsiUtilCore.NULL_PSI_ELEMENT;
          child = null; // do not want to process children
          break;
        }
      }

      boolean startChildrenVisiting;
      if (child == PsiUtilCore.NULL_PSI_ELEMENT) {
        startChildrenVisiting = true;
        child = element.getFirstChild();
      }
      else {
        startChildrenVisiting = false;
      }

      if (child == null) {
        if (startChildrenVisiting) {
          // leaf element
          offset += element.getTextLength();
        }

        if (elements.isEmpty()) break;
        int start = starts.pop();
        if (startOffset <= start && offset <= endOffset) {
          assert element != null;
          assert element != PsiUtilCore.NULL_PSI_ELEMENT;
          result.add(element);
        }

        element = elements.pop();
        child = children.pop();
      }
      else {
        // composite element
        if (offset > endOffset) break;
        children.push(child.getNextSibling());
        starts.push(offset);
        assert element != null;
        assert element != PsiUtilCore.NULL_PSI_ELEMENT;
        elements.push(element);
        element = child;
        child = PsiUtilCore.NULL_PSI_ELEMENT;
      }
    }

    return result;
  }


  @Nullable
  public static PsiElement findCommonParent(final PsiElement root, final int startOffset, final int endOffset) {
    if (startOffset == endOffset) return null;
    final PsiElement left = findElementAtInRoot(root, startOffset);
    PsiElement right = findElementAtInRoot(root, endOffset - 1);
    if (left == null || right == null) return null;

    PsiElement commonParent = PsiTreeUtil.findCommonParent(left, right);
    if (commonParent == null) {
      LOG.error("No common parent for "+left+" and "+right+"; root: "+root+"; startOffset: "+startOffset+"; endOffset: "+endOffset);
    }
    LOG.assertTrue(commonParent.getTextRange() != null, commonParent);

    PsiElement parent = commonParent.getParent();
    while (parent != null && commonParent.getTextRange().equals(parent.getTextRange())) {
      commonParent = parent;
      parent = parent.getParent();
    }
    return commonParent;
  }

  @Nullable
  private static PsiElement findElementAtInRoot(final PsiElement root, final int offset) {
    if (root instanceof PsiFile) {
      return ((PsiFile)root).getViewProvider().findElementAt(offset, root.getLanguage());
    }
    return root.findElementAt(offset);
  }
}
