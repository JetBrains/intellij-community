// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.fragmented;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

class UnifiedEditorRangeHighlighter {
  private final @NotNull List<Element> myPieces = new ArrayList<>();

  UnifiedEditorRangeHighlighter(@Nullable Project project,
                                @NotNull Document document1,
                                @NotNull Document document2,
                                @NotNull List<? extends HighlightRange> ranges) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    MarkupModelEx model1 = (MarkupModelEx)DocumentMarkupModel.forDocument(document1, project, false);
    MarkupModelEx model2 = (MarkupModelEx)DocumentMarkupModel.forDocument(document2, project, false);
    init(model1, model2, ranges);
  }

  private void init(@Nullable MarkupModelEx model1,
                    @Nullable MarkupModelEx model2,
                    @NotNull List<? extends HighlightRange> ranges) {
    for (HighlightRange range : ranges) {
      if (range.getSide().isLeft()) {
        if (model1 != null) processRange(model1, range);
      }
      else {
        if (model2 != null) processRange(model2, range);
      }
    }
  }

  private void processRange(@NotNull MarkupModelEx model, @NotNull HighlightRange range) {
    final TextRange base = range.getBase();
    final TextRange changed = range.getChanged();
    final int changedLength = changed.getEndOffset() - changed.getStartOffset();

    model.processRangeHighlightersOverlappingWith(changed.getStartOffset(), changed.getEndOffset(), marker -> {
      int relativeStart = Math.max(marker.getStartOffset() - changed.getStartOffset(), 0);
      int relativeEnd = Math.min(marker.getEndOffset() - changed.getStartOffset(), changedLength);

      int newStart = base.getStartOffset() + relativeStart;
      int newEnd = base.getStartOffset() + relativeEnd;

      if (newEnd - newStart < 0) return true;
      if (newEnd == newStart && !marker.isAfterEndOfLine()) return true;

      if (myPieces.size() % 1014 == 0) ProgressManager.checkCanceled();
      myPieces.add(new Element(marker, newStart, newEnd));

      return true;
    });
  }

  public static void erase(@Nullable Project project, @NotNull Document document) {
    MarkupModel model = DocumentMarkupModel.forDocument(document, project, true);
    model.removeAllHighlighters();
  }

  public void apply(@Nullable Project project, @NotNull Document document) {
    MarkupModelEx model = (MarkupModelEx)DocumentMarkupModel.forDocument(document, project, true);

    for (Element piece : myPieces) {
      RangeHighlighterEx delegate = piece.getDelegate();
      if (!delegate.isValid()) continue;

      model.addRangeHighlighterAndChangeAttributes(
        delegate.getTextAttributesKey(), piece.getStart(), piece.getEnd(), delegate.getLayer(),
        delegate.getTargetArea(), false, ex -> {
          ex.copyFrom(delegate);
        });
    }
  }

  private static class Element {
    private final @NotNull RangeHighlighterEx myDelegate;

    private final int myStart;
    private final int myEnd;

    Element(@NotNull RangeHighlighterEx delegate, int start, int end) {
      myDelegate = delegate;
      myStart = start;
      myEnd = end;
    }

    public @NotNull RangeHighlighterEx getDelegate() {
      return myDelegate;
    }

    public int getStart() {
      return myStart;
    }

    public int getEnd() {
      return myEnd;
    }
  }
}
