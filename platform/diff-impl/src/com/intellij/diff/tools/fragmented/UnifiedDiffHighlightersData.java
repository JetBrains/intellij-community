// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.fragmented;

import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class UnifiedDiffHighlightersData {
  private final @Nullable EditorHighlighter myHighlighter;
  private final @Nullable UnifiedEditorRangeHighlighter myRangeHighlighter;

  public UnifiedDiffHighlightersData(@Nullable EditorHighlighter highlighter,
                                     @Nullable UnifiedEditorRangeHighlighter rangeHighlighter) {
    myHighlighter = highlighter;
    myRangeHighlighter = rangeHighlighter;
  }

  public static void apply(@Nullable Project project, @NotNull EditorEx editor, @Nullable UnifiedDiffHighlightersData unifiedDiffHighlightersData) {
    EditorHighlighter highlighter = unifiedDiffHighlightersData != null ? unifiedDiffHighlightersData.myHighlighter : null;
    UnifiedEditorRangeHighlighter rangeHighlighter = unifiedDiffHighlightersData != null ? unifiedDiffHighlightersData.myRangeHighlighter : null;

    if (highlighter != null) {
      editor.setHighlighter(highlighter);
    }
    else {
      editor.setHighlighter(DiffUtil.createEmptyEditorHighlighter());
    }

    UnifiedEditorRangeHighlighter.erase(project, editor.getDocument());
    if (rangeHighlighter != null) {
      rangeHighlighter.apply(project, editor.getDocument());
    }
  }
}
