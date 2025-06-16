// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.*;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.reference.SoftReference;
import com.intellij.util.Processor;
import com.intellij.util.containers.ConcurrentLongObjectMap;
import com.intellij.util.containers.Stack;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntStack;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Internal class for collecting PSI elements inside the file for highlighting purposes.
 * Optimized for repeated requests (caches the result in the PSI user data).
 * Since this caching is highly highlighting-specific and full of peculiarities, do not use.
 * Instead, see {@link CollectHighlightsUtil#getElementsInRange(PsiElement, int, int)} for more strait-forward algorithm.
 */
@ApiStatus.Internal
public final class Divider {
  private static final int STARTING_TREE_HEIGHT = 10;

  public record DividedElements(@NotNull PsiFile psiRoot, long priorityRange,
                                @NotNull List<? extends @NotNull PsiElement> inside,
                                @NotNull LongList insideRanges,
                                @NotNull List<? extends @NotNull PsiElement> outside,
                                @NotNull LongList outsideRanges,
                                @NotNull List<? extends @NotNull PsiElement> parents,
                                @NotNull LongList parentRanges) {
  }

  // psiRoot user data stores PSI modification stamp, map(restrictRange -> DividedElements
  private record CachedStampedMap(long modificationStamp, @NotNull ConcurrentLongObjectMap<Reference<DividedElements>> elements) {}
  private static final Key<CachedStampedMap> CACHED_DIVIDED_ELEMENTS_KEY = Key.create("CACHED_DIVIDED_ELEMENTS");

  public static void divideInsideAndOutsideAllRoots(@NotNull PsiFile psiFile,
                                                    @NotNull TextRange restrictRange,
                                                    @NotNull TextRange priorityRange,
                                                    @Nullable Predicate<? super PsiFile> rootFilter,
                                                    @NotNull Processor<? super DividedElements> processor) {
    FileViewProvider viewProvider = psiFile.getViewProvider();
    for (PsiFile root : viewProvider.getAllFiles()) {
      if (rootFilter == null || !rootFilter.test(root)) {
        continue;
      }
      divideInsideAndOutsideInOneRoot(root, TextRangeScalarUtil.toScalarRange(restrictRange), TextRangeScalarUtil.toScalarRange(priorityRange), processor);
    }
  }

  @ApiStatus.Internal
  public static void divideInsideAndOutsideInOneRoot(@NotNull PsiFile root,
                                              long restrictRange,
                                              long priorityRange,
                                              @NotNull Processor<? super DividedElements> processor) {
    long modificationStamp = root.getModificationStamp();
    ConcurrentLongObjectMap<Reference<DividedElements>> cachedMap;
    while (true) {
      CachedStampedMap cache = root.getUserData(CACHED_DIVIDED_ELEMENTS_KEY);
      if (cache != null && cache.modificationStamp == modificationStamp) {
        cachedMap = cache.elements();
        break;
      }
      ((UserDataHolderEx)root).replace(CACHED_DIVIDED_ELEMENTS_KEY, cache, new CachedStampedMap(modificationStamp, ConcurrentCollectionFactory.createConcurrentLongObjectMap()));
    }
    DividedElements cached = SoftReference.dereference(cachedMap.get(restrictRange));
    DividedElements elements;
    if (cached != null && TextRangeScalarUtil.contains(cached.priorityRange, priorityRange)) {
      elements = cached;
    }
    else {
      List<PsiElement> inside = new ArrayList<>();
      LongList insideRanges = new LongArrayList();
      List<PsiElement> outside = new ArrayList<>();
      LongList outsideRanges = new LongArrayList();
      List<PsiElement> parents = new ArrayList<>();
      LongList parentRanges = new LongArrayList();
      divideInsideAndOutsideInOneRoot(root, restrictRange, priorityRange, inside, insideRanges, outside, outsideRanges, parents, parentRanges);
      elements = new DividedElements(root, priorityRange, inside, insideRanges, outside, outsideRanges, parents, parentRanges);
      cachedMap.put(restrictRange, new java.lang.ref.SoftReference<>(elements));
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
                                                      @NotNull LongList outParentRanges) {
    int startOffset = TextRangeScalarUtil.startOffset(restrictRange);
    int endOffset = TextRangeScalarUtil.endOffset(restrictRange);

    List<Condition<PsiElement>> filters = CollectHighlightsUtil.EP_NAME.getExtensionList();

    IntStack starts = new IntArrayList(STARTING_TREE_HEIGHT);
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

        int start = starts.popInt();
        if (startOffset <= start && offset <= endOffset) {
          if (TextRangeScalarUtil.containsRange(priorityRange, start, offset)) {
            inside.add(element);
            insideRanges.add(TextRangeScalarUtil.toScalarRange(start, offset));
          }
          else {
            outside.add(element);
            outsideRanges.add(TextRangeScalarUtil.toScalarRange(start, offset));
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

    PsiElement parent = !outside.isEmpty() ? outside.get(outside.size() - 1) :
                        !inside.isEmpty() ? inside.get(inside.size() - 1) :
                        CollectHighlightsUtil.findCommonParent(root, startOffset, endOffset);
    while (parent != null && !(parent instanceof PsiFile)) {
      parent = parent.getParent();
      if (parent != null) {
        outParents.add(parent);
        TextRange textRange = parent.getTextRange();
        assert textRange != null : "Text range for " + parent + " is null. " + parent.getClass() +"; root: "+root+": "+root.getVirtualFile();
        outParentRanges.add(TextRangeScalarUtil.toScalarRange(textRange));
      }
    }

    assert inside.size() == insideRanges.size();
    assert outside.size() == outsideRanges.size();
    assert outParents.size() == outParentRanges.size();
  }
}
