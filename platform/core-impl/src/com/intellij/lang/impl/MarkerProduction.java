/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.lang.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ExceptionUtil;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
final class MarkerProduction extends TIntArrayList {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.impl.MarkerProduction");
  private static final int LINEAR_SEARCH_LIMIT = 20;
  private final MarkerPool myPool;
  private final MarkerOptionalData myOptionalData;

  MarkerProduction(MarkerPool pool, MarkerOptionalData optionalData) {
    super(256);
    myPool = pool;
    myOptionalData = optionalData;
  }

  void addBefore(PsiBuilderImpl.ProductionMarker marker, PsiBuilderImpl.ProductionMarker anchor) {
    insert(indexOf(anchor), marker.markerId);
  }

  private int indexOf(PsiBuilderImpl.ProductionMarker marker) {
    int idx = findLinearly(marker.markerId);
    if (idx < 0) {
      idx = indexOf(findMarkerAtLexeme(marker.getLexemeIndex(false)), marker.markerId);
    }
    if (idx < 0) {
      LOG.error("Dropped or rolled-back marker");
    }
    return idx;
  }

  private int findLinearly(int markerId) {
    int low = Math.max(0, size() - LINEAR_SEARCH_LIMIT);
    for (int i = size() - 1; i >= low; i--) {
      if (_data[i] == markerId) {
        return i;
      }
    }
    return -1;
  }

  private int findMarkerAtLexeme(int lexemeIndex) {
    int low = 0;
    int high = size() - LINEAR_SEARCH_LIMIT;

    while (low <= high) {
      int mid = (low + high) >>> 1;
      int midVal = getLexemeIndexAt(mid);

      if (midVal < lexemeIndex) low = mid + 1;
      else if (midVal > lexemeIndex) high = mid - 1;
      else return findSameLexemeGroupStart(lexemeIndex, mid);
    }
    return -1;
  }

  private int findSameLexemeGroupStart(int lexemeIndex, int prodIndex) {
    while (prodIndex > 0 && getLexemeIndexAt(prodIndex - 1) == lexemeIndex) prodIndex--;
    return prodIndex;
  }

  void addMarker(PsiBuilderImpl.ProductionMarker marker) {
    add(marker.markerId);
  }

  void rollbackTo(PsiBuilderImpl.ProductionMarker marker) {
    int idx = indexOf(marker);
    for (int i = size() - 1; i >= idx; i--) {
      int markerId = _data[i];
      if (markerId > 0) {
        myPool.freeMarker(myPool.get(markerId));
      }
    }
    remove(idx, size() - idx);
  }

  boolean hasErrorsAfter(@NotNull PsiBuilderImpl.StartMarker marker) {
    for (int i = indexOf(marker) + 1; i < size(); ++i) {
      PsiBuilderImpl.ProductionMarker m = getStartingMarkerAt(i);
      if (m != null && hasError(m)) return true;
    }
    return false;
  }

  private boolean hasError(PsiBuilderImpl.ProductionMarker marker) {
    return marker instanceof PsiBuilderImpl.ErrorItem || myOptionalData.getDoneError(marker.markerId) != null;
  }

  void dropMarker(@NotNull PsiBuilderImpl.StartMarker marker) {
    if (marker.isDone()) {
      remove(lastIndexOf(-marker.markerId));
    }
    remove(indexOf(marker));
    myPool.freeMarker(marker);
  }

  void addDone(PsiBuilderImpl.StartMarker marker, @Nullable PsiBuilderImpl.ProductionMarker anchorBefore) {
    insert(anchorBefore == null ? size() : indexOf(anchorBefore), -marker.markerId);
  }

  @Nullable
  PsiBuilderImpl.ProductionMarker getStartingMarkerAt(int index) {
    int id = get(index);
    return id > 0 ? myPool.get(id) : null;
  }

  @Nullable
  PsiBuilderImpl.StartMarker getDoneMarkerAt(int index) {
    int id = get(index);
    return id < 0 ? (PsiBuilderImpl.StartMarker)myPool.get(-id) : null;
  }

  int getLexemeIndexAt(int productionIndex) {
    int id = get(productionIndex);
    return myPool.get(Math.abs(id)).getLexemeIndex(id < 0);
  }

  void confineMarkersToMaxLexeme(int markersBefore, int lexemeIndex) {
    for (int k = markersBefore - 1; k > 1; k--) {
      int id = _data[k];
      PsiBuilderImpl.ProductionMarker marker = myPool.get(Math.abs(id));
      boolean done = id < 0;
      if (marker.getLexemeIndex(done) < lexemeIndex) break;
      
      marker.setLexemeIndex(lexemeIndex, done);
    }
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  void doHeavyChecksOnMarkerDone(@NotNull PsiBuilderImpl.StartMarker doneMarker, @Nullable PsiBuilderImpl.StartMarker anchorBefore) {
    int idx = indexOf(doneMarker);

    int endIdx = size();
    if (anchorBefore != null) {
      endIdx = indexOf(anchorBefore);
      if (idx > endIdx) {
        LOG.error("'Before' marker precedes this one.");
      }
    }

    for (int i = endIdx - 1; i > idx; i--) {
      PsiBuilderImpl.ProductionMarker item = getStartingMarkerAt(i);
      if (item instanceof PsiBuilderImpl.StartMarker) {
        PsiBuilderImpl.StartMarker otherMarker = (PsiBuilderImpl.StartMarker)item;
        if (!otherMarker.isDone()) {
          Throwable debugAllocThis = myOptionalData.getAllocationTrace(doneMarker);
          Throwable currentTrace = new Throwable();
          if (debugAllocThis != null) {
            ExceptionUtil.makeStackTraceRelative(debugAllocThis, currentTrace).printStackTrace(System.err);
          }
          Throwable debugAllocOther = myOptionalData.getAllocationTrace(otherMarker);
          if (debugAllocOther != null) {
            ExceptionUtil.makeStackTraceRelative(debugAllocOther, currentTrace).printStackTrace(System.err);
          }
          LOG.error("Another not done marker added after this one. Must be done before this.");
        }
      }
    }
  }

  void assertNoDoneMarkerAround(@NotNull PsiBuilderImpl.StartMarker pivot) {
    int pivotIndex = indexOf(pivot);
    for (int i = pivotIndex + 1; i < size(); i++) {
      PsiBuilderImpl.StartMarker m = getDoneMarkerAt(i);
      if (m != null && m.myLexemeIndex <= pivot.myLexemeIndex && indexOf(m) < pivotIndex) {
        throw new AssertionError("There's a marker of type '" + m.getTokenType() + "' that starts before and finishes after the current marker. See cause for its allocation trace.", myOptionalData.getAllocationTrace(m));
      }
    }
  }

}
