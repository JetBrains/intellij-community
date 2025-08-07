// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.comments;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Rustam Vishnyakov
 */
@ApiStatus.Internal
public final class CommentWrapper {
  private static final int MAX_SPLIT_ITERATIONS = 10;

  public static @Nullable String wrapCommentLines(@NotNull CommentLineDataBuilder commentLineDataBuilder, int rightMargin) {
    List<CommentLineData> lines = commentLineDataBuilder.getLines();
    if (!isWrappingNeeded(lines, rightMargin)) return null;
    List<CommentLineData> mergedLines = mergeLines(lines, rightMargin);
    StringBuilder docBuilder = new StringBuilder();
    boolean atStart = true;
    for (CommentLineData lineData : mergedLines) {
      if (atStart) {
        atStart = false;
      }
      else {
        docBuilder.append('\n');
      }
      splitLine(lineData, commentLineDataBuilder, docBuilder, rightMargin, 0);
    }
    return docBuilder.toString();
  }


  private static boolean isWrappingNeeded(List<CommentLineData> lines, int rightMargin) {
    for (CommentLineData lineData : lines) {
      if (lineData.getLineLength() > rightMargin) return true;
    }
    return false;
  }

  private static List<CommentLineData> mergeLines(List<CommentLineData> originalLines, int rightMargin) {
    List<CommentLineData> result = new ArrayList<>(originalLines.size());
    CommentLineData lastLine = null;
    for (CommentLineData line : originalLines) {
      if (lastLine != null && lastLine.hasText() && lastLine.getLineLength() > rightMargin && line.canBeMergedWithPrevious()) {
        lastLine.merge(line);
      }
      else {
        result.add(line);
        lastLine = line;
      }
    }
    return result;
  }

  private static void splitLine(CommentLineData lineData,
                                CommentLineDataBuilder lineDataBuilder,
                                StringBuilder docBuilder,
                                int rightMargin,
                                int depth) {
    if (lineData.getLineLength() >= rightMargin && depth < MAX_SPLIT_ITERATIONS) {
      Pair<String,String> chunks = lineData.splitLine(rightMargin);
      if (chunks != null) {
        String linePrefix = lineData.getLinePrefix();
        if (!linePrefix.isEmpty() && (linePrefix.length() + chunks.second.length()) < lineData.getLineLength()) {
          docBuilder.append(chunks.first).append('\n');
          String newLine = linePrefix + chunks.second;
          CommentLineData newLineData = lineDataBuilder.parseLine(newLine);
          newLineData.setTagLine(lineData.isTagLine());
          splitLine(newLineData, lineDataBuilder, docBuilder, rightMargin, depth + 1);
          return;
        }
      }
    }
    docBuilder.append(lineData.getLine());
  }

}
