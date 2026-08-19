// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl;

import com.intellij.find.FindModel;
import com.intellij.find.FindModel.SearchContext;
import com.intellij.find.FindResult;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.text.ImmutableCharSequence;
import it.unimi.dsi.fastutil.ints.Int2IntMap.Entry;
import it.unimi.dsi.fastutil.ints.Int2IntRBTreeMap;
import it.unimi.dsi.fastutil.ints.Int2IntSortedMap;
import it.unimi.dsi.fastutil.ints.IntComparators;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Rejects the find results that fall in a region the user asked to search outside of, for the {@code EXCEPT_*} search
 * contexts.
 * <p>
 * It does not record where the comments and string literals are. It records where <em>the search string occurs</em>
 * inside them: the same search is run again in the mirror-image context ({@code IN_COMMENTS} for
 * {@code EXCEPT_COMMENTS}, and so on) and every occurrence it finds becomes a range to skip. A result is then rejected
 * when it lands on one of those. Working in match ranges rather than token ranges keeps this independent of how the
 * file is lexed, at the price of having to run a whole second search.
 * <p>
 * That second search covers the entire file and happens before the first result is handed out, so building one of
 * these is not cheap. {@link FindManagerBase} keeps the instance in a per-thread cache on the {@link FindModel} and
 * asks {@link #isAcceptableFor} whether the cached one still applies.
 *
 * @param myFile          the file the ranges were collected from
 * @param myFindModel     a copy of the model they were collected for, taken so that later edits to the caller's model
 *                        do not silently invalidate them
 * @param myText          an immutable snapshot of the text they refer to
 * @param mySkipRangesSet occurrences to skip as {@code start -> end}, keyed in descending order of start offset
 */
record FindExceptCommentsOrLiteralsData(@NotNull VirtualFile myFile,
                                        @NotNull FindModel myFindModel,
                                        @NotNull CharSequence myText,
                                        @NotNull Int2IntSortedMap mySkipRangesSet) implements Predicate<FindResult> {

  /**
   * Collects the ranges to skip for whichever contexts {@code model} excludes; {@code EXCEPT_COMMENTS_AND_STRING_LITERALS}
   * runs both passes into the same set.
   */
  @Contract("_, _, _, _ -> new")
  static @NotNull FindExceptCommentsOrLiteralsData create(@NotNull VirtualFile file,
                                                          @NotNull FindModel model,
                                                          @NotNull CharSequence text,
                                                          @NotNull CommentsAndLiteralsSearcher commentsAndLiteralsSearcher) {
    Int2IntSortedMap skipRangesSet = new Int2IntRBTreeMap(IntComparators.OPPOSITE_COMPARATOR);

    if (model.isExceptComments() || model.isExceptCommentsAndStringLiterals()) {
      addRanges(file, model, text, skipRangesSet, SearchContext.IN_COMMENTS, commentsAndLiteralsSearcher);
    }

    if (model.isExceptStringLiterals() || model.isExceptCommentsAndStringLiterals()) {
      addRanges(file, model, text, skipRangesSet, SearchContext.IN_STRING_LITERALS, commentsAndLiteralsSearcher);
    }

    return new FindExceptCommentsOrLiteralsData(file, model.clone(), ImmutableCharSequence.asImmutable(text), skipRangesSet);
  }

  /**
   * Adds every occurrence lying in {@code searchContext} to {@code result}.
   * <p>
   * The model is cloned because the search context has to be flipped to the one being excluded, and the caller's model
   * must keep its own. Direction is forced forward: the collector walks the file once from the start, and which way the
   * user happens to be searching does not change the set of ranges.
   */
  private static void addRanges(@NotNull VirtualFile file,
                                @NotNull FindModel model,
                                @NotNull CharSequence text,
                                @NotNull Int2IntSortedMap result,
                                @NotNull SearchContext searchContext,
                                @NotNull CommentsAndLiteralsSearcher searcher) {
    FindModel clonedModel = model.clone();
    clonedModel.setSearchContext(searchContext);
    clonedModel.setForward(true);
    searcher.collectOccurrences(text, clonedModel, file, result);
  }

  /**
   * Whether the collected ranges still describe this search, i.e. whether the cached instance can be reused instead of
   * scanning the file again.
   * <p>
   * The text is compared by length alone. That is deliberate -- comparing the content would cost as much as the scan
   * this check exists to avoid -- but it does mean an edit that leaves the length unchanged is not noticed here.
   * Callers drop the cache when the document changes, which is what actually covers that case.
   */
  boolean isAcceptableFor(@NotNull FindModel model, @NotNull VirtualFile file, @NotNull CharSequence text) {
    return Comparing.equal(myFile, file) &&
           myFindModel.equals(model) &&
           myText.length() == text.length()
      ;
  }

  /**
   * {@code true} to keep {@code input}, {@code false} to reject it as falling inside an excluded region. A result that
   * found nothing is kept: there is nothing to exclude.
   * <p>
   * {@code mySkipRangesSet} is keyed in descending order of start offset, so the tail map from the result's start
   * offset yields exactly the ranges that begin at or before it, nearest first. A range rejects the result when it
   * reaches into it; the walk stops at the first range that ends before the result begins, since those further back
   * cannot reach it either.
   */
  @Override
  public boolean test(@Nullable FindResult input) {
    if (input == null || !input.isStringFound()) {
      return true;
    }
    Int2IntSortedMap map = mySkipRangesSet.tailMap(input.getStartOffset());
    for (Entry e : map.int2IntEntrySet()) {
      // [e.key, e.value] intersect with [input.start, input.end]
      int start = e.getIntKey();
      int end = e.getIntValue();
      if (start <= input.getStartOffset() && (input.getStartOffset() <= end || end >= input.getEndOffset())) {
        return false;
      }
      if (end <= input.getStartOffset()) {
        break;
      }
    }
    return true;
  }
}
