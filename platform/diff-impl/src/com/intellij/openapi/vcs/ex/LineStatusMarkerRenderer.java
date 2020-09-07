// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.ex;

import com.intellij.diff.util.DiffDrawUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DefaultFlagsProvider;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.LineStatusMarkerDrawUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.IntPair;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import kotlin.Unit;
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
    MarkupModel markupModel = DocumentMarkupModel.forDocument(document, myTracker.getProject(), true);
    myHighlighter = markupModel.addRangeHighlighter(null, 0, document.getTextLength(), DiffDrawUtil.LST_LINE_MARKER_LAYER,
                                                    HighlighterTargetArea.LINES_IN_RANGE);
    myHighlighter.setGreedyToLeft(true);
    myHighlighter.setGreedyToRight(true);

    myHighlighter.setLineMarkerRenderer(new MyActiveGutterRenderer());

    if (myEditorFilter != null) myHighlighter.setEditorFilter(myEditorFilter);

    myUpdateQueue = new MergingUpdateQueue("LineStatusMarkerRenderer", 100, true, ANY_COMPONENT, myTracker.getDisposable());

    Disposer.register(myTracker.getDisposable(), () -> {
      myDisposed = true;
      destroyHighlighters();
    });

    scheduleUpdate();
  }

  public void scheduleUpdate() {
    myUpdateQueue.queue(new Update("update") {
      @Override
      public void run() {
        updateHighlighters();
      }
    });
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
        MarkupModel markupModel = DocumentMarkupModel.forDocument(myTracker.getDocument(), myTracker.getProject(), true);
        for (Range range : ranges) {
          RangeHighlighter highlighter = LineStatusMarkerDrawUtil.createTooltipRangeHighlighter(range, markupModel);
          if (myEditorFilter != null) highlighter.setEditorFilter(myEditorFilter);
          myTooltipHighlighters.add(highlighter);
        }
      }
    }
  }

  private void destroyHighlighters() {
    disposeHighlighter(myHighlighter);

    for (RangeHighlighter highlighter: myTooltipHighlighters) {
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

    int lineHeight = editor.getLineHeight();
    int triangleGap = lineHeight / 3;

    Rectangle clip = new Rectangle(0, y - lineHeight, editor.getComponent().getWidth(), lineHeight * 2);
    List<ChangesBlock<Unit>> blocks = VisibleRangeMerger.merge(editor, ranges, clip);

    List<Range> result = new ArrayList<>();
    for (ChangesBlock<Unit> block : blocks) {
      ChangedLines<Unit> firstChange = block.changes.get(0);
      ChangedLines<Unit> lastChange = block.changes.get(block.changes.size() - 1);

      int startY = firstChange.y1;
      int endY = lastChange.y2;

      // "empty" range for deleted block
      if (firstChange.y1 == firstChange.y2) {
        startY -= triangleGap;
      }
      if (lastChange.y1 == lastChange.y2) {
        endY += triangleGap;
      }

      if (startY <= y && endY > y) {
        result.addAll(block.ranges);
      }
    }
    return result;
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

    int yStart = editor.visualLineToY(lineNum);
    Rectangle clip = new Rectangle(bounds.x, yStart, bounds.width, editor.getLineHeight());

    List<ChangesBlock<Unit>> blocks = VisibleRangeMerger.merge(editor, ranges, clip);
    if (blocks.isEmpty()) return null;

    List<ChangedLines<Unit>> changes = blocks.get(0).changes;
    int y = changes.get(0).y1;
    int endY = changes.get(changes.size() - 1).y2;
    if (y == endY) {
      endY += editor.getLineHeight();
    }

    IntPair area = LineStatusMarkerDrawUtil.getGutterArea(editor);
    return new Rectangle(area.first, y, area.second - area.first, endY - y);
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
}
