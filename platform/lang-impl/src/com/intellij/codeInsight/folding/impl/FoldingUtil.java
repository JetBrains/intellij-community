/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

class FoldingUtil {
  private FoldingUtil() {}

  @Nullable
  public static FoldRegion findFoldRegion(Editor editor, int startOffset, int endOffset) {
    FoldRegion[] foldRegions = ((FoldingModelEx)editor.getFoldingModel()).getAllFoldRegionsIncludingInvalid();
    for (FoldRegion region : foldRegions) {
      if (region.isValid() &&
          region.getStartOffset() == startOffset
          && region.getEndOffset() == endOffset) {
        return region;
      }
    }

    return null;
  }

  public static FoldRegion findFoldRegionStartingAtLine(Editor editor, int line){
    FoldRegion[] regions = editor.getFoldingModel().getAllFoldRegions();
    FoldRegion result = null;
    for (FoldRegion region : regions) {
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
}
