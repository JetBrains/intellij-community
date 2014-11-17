/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.codeInsight.folding.impl;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.RangeMarker;
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
    List<FoldRegion> list = new ArrayList<FoldRegion>();
    FoldRegion[] allRegions = editor.getFoldingModel().getAllFoldRegions();
    for (FoldRegion region : allRegions) {
      if (region.getStartOffset() <= offset && offset <= region.getEndOffset()) {
        list.add(region);
      }
    }

    FoldRegion[] regions = list.toArray(new FoldRegion[list.size()]);
    Arrays.sort(regions, Collections.reverseOrder(RangeMarker.BY_START_OFFSET));
    return regions;
  }

  public static boolean caretInsideRange(final Editor editor, final TextRange range) {
    final int offset = editor.getCaretModel().getOffset();
    return range.contains(offset) && range.getStartOffset() != offset;
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
