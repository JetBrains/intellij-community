// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.ex;

import com.intellij.diff.util.DiffDrawUtil;
import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DefaultFlagsProvider;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.LineStatusMarkerDrawUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.ui.update.DisposableUpdate;
import com.intellij.util.ui.update.MergingUpdateQueue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.diagnostic.Logger.getInstance;
import static com.intellij.util.ui.update.MergingUpdateQueue.ANY_COMPONENT;
import static java.util.Collections.emptyList;

public abstract class LineStatusMarkerRenderer {
  private static final Logger LOG = getInstance(LineStatusMarkerRenderer.class);

  public static final Key<MarkerData> TOOLTIP_KEY = Key.create("LineStatusMarkerRenderer.Tooltip.Id");
  public static final Key<Boolean> MAIN_KEY = Key.create("LineStatusMarkerRenderer.Main.Id");

  @NotNull protected final LineStatusTrackerI<?> myTracker;
  private final MarkupEditorFilter myEditorFilter;

  @NotNull private final MergingUpdateQueue myUpdateQueue;
  private boolean myDisposed;
  @NotNull private final RangeHighlighter myHighlighter;
  @NotNull private final List<RangeHighlighter> myTooltipHighlighters = new ArrayList<>();

  LineStatusMarkerRenderer(@NotNull LineStatusTrackerI<?> tracker) {
    myTracker = tracker;
    myEditorFilter = getEditorFilter();

    Document document = myTracker.getDocument();
    MarkupModelEx markupModel = (MarkupModelEx)DocumentMarkupModel.forDocument(document, myTracker.getProject(), true);
    myHighlighter = markupModel.addRangeHighlighterAndChangeAttributes(null, 0, document.getTextLength(),
                                                                       DiffDrawUtil.LST_LINE_MARKER_LAYER,
                                                                       HighlighterTargetArea.LINES_IN_RANGE,
                                                                       false, it -> {
        it.setGreedyToLeft(true);
        it.setGreedyToRight(true);

        it.setLineMarkerRenderer(new MyActiveGutterRenderer());
        if (myEditorFilter != null) it.setEditorFilter(myEditorFilter);

        // ensure key is there in MarkupModelListener.afterAdded event
        it.putUserData(MAIN_KEY, true);
      });

    myUpdateQueue = new MergingUpdateQueue("LineStatusMarkerRenderer", 100, true, ANY_COMPONENT, myTracker.getDisposable());

    Disposer.register(myTracker.getDisposable(), () -> {
      myDisposed = true;
      destroyHighlighters();
    });

    scheduleUpdate();
  }

  public void scheduleUpdate() {
    myUpdateQueue.queue(DisposableUpdate.createDisposable(myUpdateQueue, "update", () -> {
      updateHighlighters();
    }));
  }

  @RequiresEdt
  private void updateHighlighters() {
    if (myDisposed) return;

    for (RangeHighlighter highlighter : myTooltipHighlighters) {
      disposeHighlighter(highlighter);
    }
    myTooltipHighlighters.clear();

    if (shouldPaintErrorStripeMarkers()) {
      List<? extends Range> ranges = myTracker.getRanges();
      if (ranges != null) {
        MarkupModelEx markupModel = (MarkupModelEx)DocumentMarkupModel.forDocument(myTracker.getDocument(), myTracker.getProject(), true);
        for (Range range : ranges) {
          RangeHighlighter highlighter = createTooltipRangeHighlighter(range, markupModel);
          myTooltipHighlighters.add(highlighter);
        }
      }
    }
  }

  @NotNull
  private RangeHighlighter createTooltipRangeHighlighter(@NotNull Range range, @NotNull MarkupModelEx markupModel) {
    TextRange textRange = DiffUtil.getLinesRange(markupModel.getDocument(), range.getLine1(), range.getLine2(), false);
    TextAttributes attributes = new LineStatusMarkerDrawUtil.DiffStripeTextAttributes(range.getType());

    return markupModel.addRangeHighlighterAndChangeAttributes(null, textRange.getStartOffset(), textRange.getEndOffset(),
                                                              DiffDrawUtil.LST_LINE_MARKER_LAYER,
                                                              HighlighterTargetArea.LINES_IN_RANGE,
                                                              false, it -> {
        it.setThinErrorStripeMark(true);
        it.setGreedyToLeft(true);
        it.setGreedyToRight(true);

        it.setTextAttributes(attributes);
        if (myEditorFilter != null) it.setEditorFilter(myEditorFilter);

        // ensure key is there in MarkupModelListener.afterAdded event
        it.putUserData(TOOLTIP_KEY, new MarkerData(range.getType()));
      });
  }

  private void destroyHighlighters() {
    disposeHighlighter(myHighlighter);

    for (RangeHighlighter highlighter : myTooltipHighlighters) {
      disposeHighlighter(highlighter);
    }
    myTooltipHighlighters.clear();
  }

  private static void disposeHighlighter(@NotNull RangeHighlighter highlighter) {
    try {
      highlighter.dispose();
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  private boolean canDoAction(@NotNull Editor editor, @NotNull MouseEvent e) {
    List<? extends Range> ranges = getSelectedRanges(editor, e.getY());
    return !ranges.isEmpty() && canDoAction(editor, ranges, e);
  }

  private void doAction(@NotNull Editor editor, @NotNull MouseEvent e) {
    List<? extends Range> ranges = getSelectedRanges(editor, e.getY());
    if (!ranges.isEmpty()) {
      doAction(editor, ranges, e);
    }
  }

  @NotNull
  private List<? extends Range> getSelectedRanges(@NotNull Editor editor, int y) {
    List<? extends Range> ranges = myTracker.getRanges();
    if (ranges == null) return emptyList();

    return LineStatusMarkerDrawUtil.getSelectedRanges(ranges, editor, y);
  }

  protected boolean canDoAction(@NotNull Editor editor, @NotNull List<? extends Range> ranges, @NotNull MouseEvent e) {
    return false;
  }

  protected void doAction(@NotNull Editor editor, @NotNull List<? extends Range> ranges, @NotNull MouseEvent e) {
  }

  @Nullable
  protected MarkupEditorFilter getEditorFilter() {
    return null;
  }

  //
  // Gutter painting
  //

  private Rectangle calcBounds(Editor editor, int lineNum, Rectangle bounds) {
    List<? extends Range> ranges = myTracker.getRanges();
    if (ranges == null) return null;

    return LineStatusMarkerDrawUtil.calcBounds(ranges, editor, lineNum);
  }

  protected boolean shouldPaintGutter() {
    return true;
  }

  protected boolean shouldPaintErrorStripeMarkers() {
    return shouldPaintGutter();
  }

  protected void paint(@NotNull Editor editor, @NotNull Graphics g) {
    LineStatusMarkerDrawUtil.paintDefault(editor, g, myTracker, DefaultFlagsProvider.DEFAULT, 0);
  }


  private class MyActiveGutterRenderer implements ActiveGutterRenderer {
    @Override
    public void paint(@NotNull Editor editor, @NotNull Graphics g, @NotNull Rectangle r) {
      if (shouldPaintGutter()) {
        LineStatusMarkerRenderer.this.paint(editor, g);
      }
    }

    @Override
    public boolean canDoAction(@NotNull Editor editor, @NotNull MouseEvent e) {
      return shouldPaintGutter() &&
             LineStatusMarkerRenderer.this.canDoAction(editor, e);
    }

    @Override
    public void doAction(@NotNull Editor editor, @NotNull MouseEvent e) {
      if (shouldPaintGutter()) {
        LineStatusMarkerRenderer.this.doAction(editor, e);
      }
    }

    @Nullable
    @Override
    public Rectangle calcBounds(@NotNull Editor editor, int lineNum, @NotNull Rectangle preferredBounds) {
      if (!shouldPaintGutter()) return new Rectangle(-1, -1, 0, 0);
      return LineStatusMarkerRenderer.this.calcBounds(editor, lineNum, preferredBounds);
    }

    @NotNull
    @Override
    public String getAccessibleName() {
      return DiffBundle.message("vcs.marker.changed.line");
    }
  }

  public static class MarkerData {
    public final byte type;

    public MarkerData(byte type) {
      this.type = type;
    }
  }
}
