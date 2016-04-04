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

package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
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
  public static final ExtensionPointName<Condition<PsiElement>> EP_NAME = ExtensionPointName.create("com.intellij.elementsToHighlightFilter");

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
    if (commonParent == null) return new ArrayList<PsiElement>();
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

  @NotNull
  private static List<PsiElement> getElementsToHighlight(@NotNull PsiElement commonParent, final int startOffset, final int endOffset) {
    final List<PsiElement> result = new ArrayList<PsiElement>();
    final int currentOffset = commonParent.getTextRange().getStartOffset();
    final Condition<PsiElement>[] filters = Extensions.getExtensions(EP_NAME);

    int offset = currentOffset;

    final TIntStack starts = new TIntStack(STARTING_TREE_HEIGHT);
    final Stack<PsiElement> elements = new Stack<PsiElement>(STARTING_TREE_HEIGHT);
    final Stack<PsiElement> children = new Stack<PsiElement>(STARTING_TREE_HEIGHT);
    PsiElement element = commonParent;

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
