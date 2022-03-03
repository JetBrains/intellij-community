// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.lang.Language;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.reference.SoftReference;
import com.intellij.util.Processor;
import com.intellij.util.containers.IntStack;
import com.intellij.util.containers.Stack;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

@ApiStatus.Internal
public final class Divider {
  private static final int STARTING_TREE_HEIGHT = 10;

  public static final class DividedElements {
    private final long modificationStamp;
    private final long restrictRange;
    public final long priorityRange;
    public final List<PsiElement> inside = new ArrayList<>();
    final LongList insideRanges = new LongArrayList();
    public final List<PsiElement> outside = new ArrayList<>();
    final LongList outsideRanges = new LongArrayList();
    public final List<PsiElement> parents = new ArrayList<>();
    final LongList parentRanges = new LongArrayList();

    private DividedElements(long modificationStamp, long restrictRange, long priorityRange) {
      this.modificationStamp = modificationStamp;
      this.restrictRange = restrictRange;
      this.priorityRange = priorityRange;
    }
  }

  private static final Key<Reference<DividedElements>> DIVIDED_ELEMENTS_KEY = Key.create("DIVIDED_ELEMENTS");

  public static void divideInsideAndOutsideAllRoots(@NotNull PsiFile file,
                                                    @NotNull TextRange restrictRange,
                                                    @NotNull TextRange priorityRange,
                                                    @Nullable Predicate<? super PsiFile> rootFilter,
                                                    @NotNull Processor<? super DividedElements> processor) {
    FileViewProvider viewProvider = file.getViewProvider();
    for (Language language : viewProvider.getLanguages()) {
      PsiFile root = viewProvider.getPsi(language);
      if (rootFilter == null || !rootFilter.test(root)) {
        continue;
      }
      divideInsideAndOutsideInOneRoot(root, restrictRange.toScalarRange(), priorityRange.toScalarRange(), processor);
    }
  }

  static void divideInsideAndOutsideInOneRoot(@NotNull PsiFile root,
                                              long restrictRange,
                                              long priorityRange,
                                              @NotNull Processor<? super DividedElements> processor) {
    long modificationStamp = root.getModificationStamp();
    DividedElements cached = SoftReference.dereference(root.getUserData(DIVIDED_ELEMENTS_KEY));
    DividedElements elements;
    if (cached != null &&
        cached.modificationStamp == modificationStamp &&
        cached.restrictRange == restrictRange &&
        TextRange.contains(cached.priorityRange, priorityRange)) {
      elements = cached;
    }
    else {
      elements = new DividedElements(modificationStamp, restrictRange, priorityRange);
      divideInsideAndOutsideInOneRoot(root, restrictRange, priorityRange, elements.inside, elements.insideRanges, elements.outside,
                                      elements.outsideRanges, elements.parents,
                                      elements.parentRanges, true);
      root.putUserData(DIVIDED_ELEMENTS_KEY, new java.lang.ref.SoftReference<>(elements));
    }
    processor.process(elements);
  }

  private static final PsiElement HAVE_TO_GET_CHILDREN = PsiUtilCore.NULL_PSI_ELEMENT;

  private static void divideInsideAndOutsideInOneRoot(@NotNull PsiFile root,
                                                      long restrictRange,
                                                      long priorityRange,
                                                      @NotNull List<PsiElement> inside,
                                                      @NotNull LongList insideRanges,
                                                      @NotNull List<PsiElement> outside,
                                                      @NotNull LongList outsideRanges,
                                                      @NotNull List<? super PsiElement> outParents,
                                                      @NotNull LongList outParentRanges,
                                                      boolean includeParents) {
    int startOffset = TextRange.startOffset(restrictRange);
    int endOffset = TextRange.endOffset(restrictRange);

    Condition<PsiElement>[] filters = CollectHighlightsUtil.EP_NAME.getExtensions();

    IntStack starts = new IntStack(STARTING_TREE_HEIGHT);
    starts.push(startOffset);
    Stack<PsiElement> elements = new Stack<>(STARTING_TREE_HEIGHT);
    Stack<PsiElement> children = new Stack<>(STARTING_TREE_HEIGHT);
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
          if (TextRange.containsRange(priorityRange, start, offset)) {
            inside.add(element);
            insideRanges.add(TextRange.toScalarRange(start, offset));
          }
          else {
            outside.add(element);
            outsideRanges.add(TextRange.toScalarRange(start, offset));
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
          outParentRanges.add(textRange.toScalarRange());
        }
      }
    }

    assert inside.size() == insideRanges.size();
    assert outside.size() == outsideRanges.size();
    assert outParents.size() == outParentRanges.size();
  }
}
