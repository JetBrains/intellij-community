// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.lang.Language;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.reference.SoftReference;
import com.intellij.util.Processor;
import com.intellij.util.containers.Stack;
import gnu.trove.TIntStack;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.List;

public class Divider {
  private static final int STARTING_TREE_HEIGHT = 10;

  public static class DividedElements {
    private final long modificationStamp;
    @NotNull private final TextRange restrictRange;
    @NotNull private final TextRange priorityRange;
    public final List<PsiElement> inside = new ArrayList<>();
    final List<ProperTextRange> insideRanges = new ArrayList<>();
    public final List<PsiElement> outside = new ArrayList<>();
    final List<ProperTextRange> outsideRanges = new ArrayList<>();
    public final List<PsiElement> parents = new ArrayList<>();
    final List<ProperTextRange> parentRanges = new ArrayList<>();

    private DividedElements(long modificationStamp, @NotNull TextRange restrictRange, @NotNull TextRange priorityRange) {
      this.modificationStamp = modificationStamp;
      this.restrictRange = restrictRange;
      this.priorityRange = priorityRange;
    }
  }

  private static final Key<Reference<DividedElements>> DIVIDED_ELEMENTS_KEY = Key.create("DIVIDED_ELEMENTS");

  public static void divideInsideAndOutsideAllRoots(@NotNull PsiFile file,
                                                     @NotNull TextRange restrictRange,
                                                     @NotNull TextRange priorityRange,
                                                     @NotNull Condition<? super PsiFile> rootFilter,
                                                     @NotNull Processor<? super DividedElements> processor) {
    final FileViewProvider viewProvider = file.getViewProvider();
    for (Language language : viewProvider.getLanguages()) {
      final PsiFile root = viewProvider.getPsi(language);
      if (!rootFilter.value(root)) {
        continue;
      }
      divideInsideAndOutsideInOneRoot(root, restrictRange, priorityRange, processor);
    }
  }

  static void divideInsideAndOutsideInOneRoot(@NotNull PsiFile root,
                                              @NotNull TextRange restrictRange,
                                              @NotNull TextRange priorityRange,
                                              @NotNull Processor<? super DividedElements> processor) {
    long modificationStamp = root.getModificationStamp();
    DividedElements cached = SoftReference.dereference(root.getUserData(DIVIDED_ELEMENTS_KEY));
    DividedElements elements;
    if (cached == null || cached.modificationStamp != modificationStamp || !cached.restrictRange.equals(restrictRange) || !cached.priorityRange.contains(priorityRange)) {
      elements = new DividedElements(modificationStamp, restrictRange, priorityRange);
      divideInsideAndOutsideInOneRoot(root, restrictRange, priorityRange, elements.inside, elements.insideRanges, elements.outside,
                                      elements.outsideRanges, elements.parents,
                                      elements.parentRanges, true);
      root.putUserData(DIVIDED_ELEMENTS_KEY, new java.lang.ref.SoftReference<>(elements));
    }
    else {
      elements = cached;
    }
    processor.process(elements);
  }

  private static final PsiElement HAVE_TO_GET_CHILDREN = PsiUtilCore.NULL_PSI_ELEMENT;

  private static void divideInsideAndOutsideInOneRoot(@NotNull PsiFile root,
                                                      @NotNull TextRange restrictRange,
                                                      @NotNull TextRange priorityRange,
                                                      @NotNull List<PsiElement> inside,
                                                      @NotNull List<? super ProperTextRange> insideRanges,
                                                      @NotNull List<PsiElement> outside,
                                                      @NotNull List<? super ProperTextRange> outsideRanges,
                                                      @NotNull List<? super PsiElement> outParents,
                                                      @NotNull List<? super ProperTextRange> outParentRanges,
                                                      boolean includeParents) {
    int startOffset = restrictRange.getStartOffset();
    int endOffset = restrictRange.getEndOffset();

    final Condition<PsiElement>[] filters = Extensions.getExtensions(CollectHighlightsUtil.EP_NAME);

    final TIntStack starts = new TIntStack(STARTING_TREE_HEIGHT);
    starts.push(startOffset);
    final Stack<PsiElement> elements = new Stack<>(STARTING_TREE_HEIGHT);
    final Stack<PsiElement> children = new Stack<>(STARTING_TREE_HEIGHT);
    PsiElement element = root;

    PsiElement child = HAVE_TO_GET_CHILDREN;
    int offset = 0;
    while (true) {
      ProgressManager.checkCanceled();

      for (Condition<PsiElement> filter : filters) {
        if (!filter.value(element)) {
          assert child == HAVE_TO_GET_CHILDREN;
          child = null; // do not want to process children
          break;
        }
      }

      boolean startChildrenVisiting;
      if (child == HAVE_TO_GET_CHILDREN) {
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

        int start = starts.pop();
        if (startOffset <= start && offset <= endOffset) {
          if (priorityRange.containsRange(start, offset)) {
            inside.add(element);
            insideRanges.add(new ProperTextRange(start, offset));
          }
          else {
            outside.add(element);
            outsideRanges.add(new ProperTextRange(start, offset));
          }
        }

        if (elements.isEmpty()) break;
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
        child = HAVE_TO_GET_CHILDREN;
      }
    }

    if (includeParents) {
      PsiElement parent = !outside.isEmpty() ? outside.get(outside.size() - 1) :
                          !inside.isEmpty() ? inside.get(inside.size() - 1) :
                          CollectHighlightsUtil.findCommonParent(root, startOffset, endOffset);
      while (parent != null && !(parent instanceof PsiFile)) {
        parent = parent.getParent();
        if (parent != null) {
          outParents.add(parent);
          TextRange textRange = parent.getTextRange();
          assert textRange != null : "Text range for " + parent + " is null. " + parent.getClass() +"; root: "+root+": "+root.getVirtualFile();
          outParentRanges.add(ProperTextRange.create(textRange));
        }
      }
    }

    assert inside.size() == insideRanges.size();
    assert outside.size() == outsideRanges.size();
    assert outParents.size() == outParentRanges.size();
  }
}
