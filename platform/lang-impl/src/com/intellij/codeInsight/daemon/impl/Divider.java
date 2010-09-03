/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.codeInsight.daemon.impl.analysis.HighlightLevelUtil;
import com.intellij.lang.Language;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.containers.Stack;
import gnu.trove.TIntStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * User: cdr
 */
public class Divider {
  private static final int STARTING_TREE_HEIGHT = 10;

  public static void getInsideAndOutside(@NotNull PsiFile file,
                                         int startOffset,
                                         int endOffset,
                                         @NotNull TextRange range,
                                         @NotNull List<PsiElement> inside,
                                         @NotNull List<PsiElement> outside,
                                         @NotNull HighlightLevelUtil.AnalysisLevel level) {
    final FileViewProvider viewProvider = file.getViewProvider();
    for (Language language : viewProvider.getLanguages()) {
      final PsiFile psiRoot = viewProvider.getPsi(language);
      if (HighlightLevelUtil.shouldAnalyse(psiRoot, level)) {
        getInsideAndOutside(psiRoot, startOffset, endOffset, range, inside, outside);
      }
    }
  }
  
  private static void getInsideAndOutside(@NotNull PsiFile root,
                                         int startOffset,
                                         int endOffset,
                                         @NotNull TextRange range,
                                         @NotNull List<PsiElement> inside,
                                         @NotNull List<PsiElement> outside) {
    final int currentOffset = root.getTextRange().getStartOffset();
    final Condition<PsiElement>[] filters = Extensions.getExtensions(CollectHighlightsUtil.EP_NAME);

    int offset = currentOffset;

    final TIntStack starts = new TIntStack(STARTING_TREE_HEIGHT);
    final Stack<PsiElement> elements = new Stack<PsiElement>(STARTING_TREE_HEIGHT);
    final Stack<PsiElement> children = new Stack<PsiElement>(STARTING_TREE_HEIGHT);
    PsiElement element = root;

    PsiElement child = PsiUtilBase.NULL_PSI_ELEMENT;
    while (true) {
      ProgressManager.checkCanceled();

      for (Condition<PsiElement> filter : filters) {
        if (!filter.value(element)) {
          assert child == PsiUtilBase.NULL_PSI_ELEMENT;
          child = null; // do not want to process children
          break;
        }
      }

      boolean startChildrenVisiting;
      if (child == PsiUtilBase.NULL_PSI_ELEMENT) {
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
          if (range.containsRange(start, offset)) {
            inside.add(element);
          }
          else {
            outside.add(element);
          }
        }

        element = elements.pop();
        child = children.pop();
      }
      else {
        // composite element
        if (offset > endOffset) break;
        children.push(child.getNextSibling());
        starts.push(offset);
        elements.push(element);
        element = child;
        child = PsiUtilBase.NULL_PSI_ELEMENT;
      }
    }

    outside.add(root);
  }
}
