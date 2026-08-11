// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.SmartStripTrailingSpacesFilter;
import com.intellij.openapi.editor.StripTrailingSpacesFilter;
import com.intellij.openapi.editor.StripTrailingSpacesFilterFactory;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.project.Project;
import com.intellij.util.DocumentUtil;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

final class StripTrailingSpacesUtil {
  private static final int STRIP_TRAILING_SPACES_BULK_MODE_LINES_LIMIT = 1_000;

  static boolean stripTrailingSpaces(
    @Nullable Project project,
    @NotNull DocumentEx document,
    boolean inChangedLinesOnly,
    int @Nullable [] caretOffsets
  ) {
    List<StripTrailingSpacesFilter> filters = new ArrayList<>();
    StripTrailingSpacesFilter specialFilter = null;
    List<StripTrailingSpacesFilterFactory> factories = StripTrailingSpacesFilterFactory.EXTENSION_POINT.getExtensionList();
    for (StripTrailingSpacesFilterFactory filterFactory : factories) {
      StripTrailingSpacesFilter filter = filterFactory.createFilter(project, document);
      if (specialFilter == null &&
          (filter == StripTrailingSpacesFilter.NOT_ALLOWED || filter == StripTrailingSpacesFilter.POSTPONED)) {
        specialFilter = filter;
      } else if (filter == StripTrailingSpacesFilter.ENFORCED_REMOVAL) {
        specialFilter = null;
        filters.clear();
        break;
      } else {
        filters.add(filter);
      }
    }
    if (specialFilter != null) {
      return specialFilter == StripTrailingSpacesFilter.NOT_ALLOWED;
    }
    Int2IntMap caretPositions = null;
    if (caretOffsets != null) {
      caretPositions = new Int2IntOpenHashMap(caretOffsets.length);
      for (int caretOffset : caretOffsets) {
        int line = document.getLineNumber(caretOffset);
        // need to remember only maximum caret offset on a line
        caretPositions.put(line, Math.max(caretOffset, caretPositions.get(line)));
      }
    }
    int lineCount = document.getLineCount();
    int[] targetOffsets = new int[lineCount * 2];
    int targetOffsetPos = 0;
    boolean markAsNeedsStrippingLater = false;
    CharSequence text = document.getImmutableCharSequence();
    for (int line = 0; line < lineCount; line++) {
      int maxSpacesToLeave = getMaxSpacesToLeave(line, filters);
      if (inChangedLinesOnly && !document.isLineModified(line) || maxSpacesToLeave < 0) {
        continue;
      }
      int whiteSpaceStart = -1;
      int lineEnd = document.getLineEndOffset(line);
      int lineStart = document.getLineStartOffset(line);
      for (int offset = lineEnd - 1; offset >= lineStart; offset--) {
        char c = text.charAt(offset);
        if (c != ' ' && c != '\t') {
          break;
        }
        whiteSpaceStart = offset;
      }
      if (whiteSpaceStart == -1) {
        continue;
      }
      if (caretPositions != null) {
        int caretPosition = caretPositions.get(line);
        if (whiteSpaceStart < caretPosition) {
          markAsNeedsStrippingLater = true;
          continue;
        }
      }
      int finalStart = whiteSpaceStart + maxSpacesToLeave;
      if (finalStart < lineEnd) {
        targetOffsets[targetOffsetPos++] = finalStart;
        targetOffsets[targetOffsetPos++] = lineEnd;
      }
    }
    int finalTargetOffsetPos = targetOffsetPos;
    boolean executeInBulk = finalTargetOffsetPos > STRIP_TRAILING_SPACES_BULK_MODE_LINES_LIMIT * 2;
    // Document must be unblocked by now. If not, some Save handler attempted to modify PSI
    // which should have been caught by assertion in com.intellij.pom.core.impl.PomModelImpl.runTransaction
    DocumentUtil.writeInRunUndoTransparentAction(
      () -> DocumentUtil.executeInBulk(
        document,
        executeInBulk,
        () -> {
          int pos = finalTargetOffsetPos;
          while (pos > 0) {
            int endOffset = targetOffsets[--pos];
            int startOffset = targetOffsets[--pos];
            document.deleteString(startOffset, endOffset);
          }
        }
      )
    );
    return markAsNeedsStrippingLater;
  }

  private static int getMaxSpacesToLeave(int line, @NotNull List<? extends StripTrailingSpacesFilter> filters) {
    for (StripTrailingSpacesFilter filter : filters) {
      if (filter instanceof SmartStripTrailingSpacesFilter) {
        return ((SmartStripTrailingSpacesFilter)filter).getTrailingSpacesToLeave(line);
      } else  if (!filter.isStripSpacesAllowedForLine(line)) {
        return -1;
      }
    }
    return 0;
  }

  private StripTrailingSpacesUtil() {
  }
}
