/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.tools.fragmented;

import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.Side;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

class UnifiedEditorRangeHighlighter {
  @NotNull private final List<SyntaxElement> mySyntax = new ArrayList<>();
  @NotNull private final List<MarkupElement> myMarkup = new ArrayList<>();

  UnifiedEditorRangeHighlighter(@Nullable Project project,
                                @NotNull DocumentContent content1,
                                @NotNull DocumentContent content2,
                                @NotNull List<HighlightRange> ranges) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    initSyntax(project, Side.LEFT, content1, ranges);
    initSyntax(project, Side.RIGHT, content2, ranges);

    initMarkup(project, Side.LEFT, content1, ranges);
    initMarkup(project, Side.RIGHT, content2, ranges);
  }

  private void initSyntax(@Nullable Project project,
                          @NotNull Side side,
                          @NotNull DocumentContent content,
                          @NotNull List<HighlightRange> ranges) {
    EditorHighlighter highlighter = DiffUtil.initEditorHighlighter(project, content, content.getDocument().getImmutableCharSequence());
    if (highlighter == null) return;

    for (HighlightRange range : ranges) {
      if (range.getSide() == side) {
        processSyntaxRange(highlighter, range);
      }
    }
  }

  private void initMarkup(@Nullable Project project,
                          @NotNull Side side,
                          @NotNull DocumentContent content,
                          @NotNull List<HighlightRange> ranges) {
    MarkupModelEx model = (MarkupModelEx)DocumentMarkupModel.forDocument(content.getDocument(), project, false);
    if (model == null) return;

    for (HighlightRange range : ranges) {
      if (range.getSide() == side) {
        processMarkupRange(model, range);
      }
    }
  }

  private void processSyntaxRange(@NotNull EditorHighlighter highlighter, @NotNull HighlightRange range) {
    final TextRange changed = range.getChanged();

    HighlighterIterator iterator = highlighter.createIterator(changed.getStartOffset());
    while (!iterator.atEnd() && iterator.getStart() < changed.getEndOffset()) {
      TextRange newRange = getNewRange(range, iterator.getStart(), iterator.getEnd());
      if (newRange != null) {
        mySyntax.add(new SyntaxElement(iterator.getTextAttributes(), newRange.getStartOffset(), newRange.getEndOffset()));
      }
      iterator.advance();
    }
  }

  private void processMarkupRange(@NotNull MarkupModelEx model, @NotNull HighlightRange range) {
    final TextRange changed = range.getChanged();

    model.processRangeHighlightersOverlappingWith(changed.getStartOffset(), changed.getEndOffset(), marker -> {
      TextRange newRange = getNewRange(range, marker.getStartOffset(), marker.getEndOffset());
      if (newRange != null) {
        myMarkup.add(new MarkupElement(marker, newRange.getStartOffset(), newRange.getEndOffset()));
      }
      return true;
    });
  }

  @Nullable
  private static TextRange getNewRange(@NotNull HighlightRange range, int startOffset, int endOffset) {
    final TextRange base = range.getBase();
    final TextRange changed = range.getChanged();
    final int changedLength = changed.getEndOffset() - changed.getStartOffset();

    int relativeStart = Math.max(startOffset - changed.getStartOffset(), 0);
    int relativeEnd = Math.min(endOffset - changed.getStartOffset(), changedLength);

    int newStart = base.getStartOffset() + relativeStart;
    int newEnd = base.getStartOffset() + relativeEnd;

    if (newEnd - newStart <= 0) return null;
    return new TextRange(newStart, newEnd);
  }

  public static void erase(@Nullable Project project, @NotNull Document document) {
    MarkupModel model = DocumentMarkupModel.forDocument(document, project, true);
    model.removeAllHighlighters();
  }

  public void apply(@Nullable Project project, @NotNull Document document) {
    MarkupModel model = DocumentMarkupModel.forDocument(document, project, true);

    for (SyntaxElement piece : mySyntax) {
      RangeHighlighter highlighter = model.addRangeHighlighter(piece.getStart(), piece.getEnd(), HighlighterLayer.SYNTAX + 1,
                                                               piece.getAttributes(), HighlighterTargetArea.EXACT_RANGE);
      highlighter.setGreedyToRight(true);
    }

    for (MarkupElement piece : myMarkup) {
      RangeHighlighterEx delegate = piece.getDelegate();
      if (!delegate.isValid()) continue;

      RangeHighlighter highlighter = model
        .addRangeHighlighter(piece.getStart(), piece.getEnd(), delegate.getLayer(), delegate.getTextAttributes(), delegate.getTargetArea());
      highlighter.setEditorFilter(delegate.getEditorFilter());
      highlighter.setCustomRenderer(delegate.getCustomRenderer());
      highlighter.setErrorStripeMarkColor(delegate.getErrorStripeMarkColor());
      highlighter.setErrorStripeTooltip(delegate.getErrorStripeTooltip());
      highlighter.setGutterIconRenderer(delegate.getGutterIconRenderer());
      highlighter.setLineMarkerRenderer(delegate.getLineMarkerRenderer());
      highlighter.setLineSeparatorColor(delegate.getLineSeparatorColor());
      highlighter.setThinErrorStripeMark(delegate.isThinErrorStripeMark());
      highlighter.setLineSeparatorPlacement(delegate.getLineSeparatorPlacement());
      highlighter.setLineSeparatorRenderer(delegate.getLineSeparatorRenderer());
    }
  }

  private static class MarkupElement {
    @NotNull private final RangeHighlighterEx myDelegate;

    private final int myStart;
    private final int myEnd;

    MarkupElement(@NotNull RangeHighlighterEx delegate, int start, int end) {
      myDelegate = delegate;
      myStart = start;
      myEnd = end;
    }

    @NotNull
    public RangeHighlighterEx getDelegate() {
      return myDelegate;
    }

    public int getStart() {
      return myStart;
    }

    public int getEnd() {
      return myEnd;
    }
  }

  private static class SyntaxElement {
    @NotNull private final TextAttributes myAttributes;

    private final int myStart;
    private final int myEnd;

    SyntaxElement(@NotNull TextAttributes attributes, int start, int end) {
      myAttributes = attributes;
      myStart = start;
      myEnd = end;
    }

    @NotNull
    private TextAttributes getAttributes() {
      return myAttributes;
    }

    private int getStart() {
      return myStart;
    }

    private int getEnd() {
      return myEnd;
    }
  }
}
