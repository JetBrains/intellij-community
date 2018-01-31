// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.folding.impl;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class FoldingUtil {
  private FoldingUtil() {}

  @Nullable
  public static FoldRegion findFoldRegion(@NotNull Editor editor, int startOffset, int endOffset) {
    FoldRegion region = editor.getFoldingModel().getFoldRegion(startOffset, endOffset);
    return region != null && region.isValid() ? region : null;
  }

  @Nullable
  public static FoldRegion findFoldRegionStartingAtLine(@NotNull Editor editor, int line){
    if (line < 0 || line >= editor.getDocument().getLineCount()) {
      return null;
    }
    FoldRegion result = null;
    FoldRegion[] regions = editor.getFoldingModel().getAllFoldRegions();
    for (FoldRegion region : regions) {
      if (!region.isValid()) {
        continue;
      }
      if (region.getDocument().getLineNumber(region.getStartOffset()) == line) {
        if (result != null) return null;
        result = region;
      }
    }
    return result;
  }

  public static FoldRegion[] getFoldRegionsAtOffset(Editor editor, int offset){
    List<FoldRegion> list = new ArrayList<>();
    FoldRegion[] allRegions = editor.getFoldingModel().getAllFoldRegions();
    for (FoldRegion region : allRegions) {
      if (region.getStartOffset() <= offset && offset <= region.getEndOffset()) {
        list.add(region);
      }
    }

    FoldRegion[] regions = list.toArray(FoldRegion.EMPTY_ARRAY);
    Arrays.sort(regions, Collections.reverseOrder(RangeMarker.BY_START_OFFSET));
    return regions;
  }

  static boolean caretInsideRange(final Editor editor, final TextRange range) {
    final int offset = editor.getCaretModel().getOffset();
    return range.contains(offset) && range.getStartOffset() != offset;
  }

  public static boolean isHighlighterFolded(@NotNull Editor editor, @NotNull RangeHighlighter highlighter) {
    int startOffset = highlighter instanceof RangeHighlighterEx ?
                      ((RangeHighlighterEx)highlighter).getAffectedAreaStartOffset() :
                      highlighter.getStartOffset();
    int endOffset = highlighter instanceof RangeHighlighterEx ?
                    ((RangeHighlighterEx)highlighter).getAffectedAreaEndOffset() :
                    highlighter.getEndOffset();
    return isTextRangeFolded(editor, new TextRange(startOffset, endOffset));
  }

  public static boolean isTextRangeFolded(@NotNull Editor editor, @NotNull TextRange range) {
    FoldRegion foldRegion = editor.getFoldingModel().getCollapsedRegionAtOffset(range.getStartOffset());
    return foldRegion != null && range.getEndOffset() <= foldRegion.getEndOffset();
  }

  /**
   * Iterates fold regions tree in a depth-first order (pre-order)
   */
  public static Iterator<FoldRegion> createFoldTreeIterator(@NotNull Editor editor) {
    final FoldRegion[] allRegions = editor.getFoldingModel().getAllFoldRegions();
    return new Iterator<FoldRegion>() {
      private int sectionStart;
      private int current;
      private int sectionEnd;

      {
        advanceSection();
      }

      private void advanceSection() {
        sectionStart = sectionEnd;
        //noinspection StatementWithEmptyBody
        for (sectionEnd = sectionStart + 1;
             sectionEnd < allRegions.length && allRegions[sectionEnd].getStartOffset() == allRegions[sectionStart].getStartOffset();
             sectionEnd++);
        current = sectionEnd;
      }

      @Override
      public boolean hasNext() {
        return current > sectionStart || sectionEnd < allRegions.length;
      }

      @Override
      public FoldRegion next() {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        if (current <= sectionStart) {
          advanceSection();
        }
        return allRegions[--current];
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }


}
