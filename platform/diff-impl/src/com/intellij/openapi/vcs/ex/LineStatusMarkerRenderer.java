// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.ex;

import com.intellij.diff.util.DiffDrawUtil;
import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.DiffColors;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.ex.VisibleRangeMerger.FlagsProvider;
import com.intellij.ui.paint.LinePainter2D;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.IntPair;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import kotlin.Unit;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.intellij.diff.util.DiffDrawUtil.lineToY;
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

  @CalledInAwt
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
          RangeHighlighter highlighter = createTooltipRangeHighlighter(range, markupModel);
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
    int visibleLineCount = ((EditorImpl)editor).getVisibleLineCount();
    boolean lastLineSelected = editor.yToVisualLine(y) == visibleLineCount - 1;
    int triangleGap = lineHeight / 3;

    Rectangle clip = new Rectangle(0, y - lineHeight, editor.getComponent().getWidth(), lineHeight * 2);
    List<ChangesBlock<Unit>> blocks = VisibleRangeMerger.merge(editor, ranges, clip);

    List<Range> result = new ArrayList<>();
    for (ChangesBlock<Unit> block : blocks) {
      ChangedLines<Unit> firstChange = block.changes.get(0);
      ChangedLines<Unit> lastChange = block.changes.get(block.changes.size() - 1);

      int line1 = firstChange.line1;
      int line2 = lastChange.line2;

      int startY = editor.visualLineToY(line1);
      int endY = editor.visualLineToY(line2);

      // "empty" range for deleted block
      if (firstChange.line1 == firstChange.line2) {
        startY -= triangleGap;
      }
      if (lastChange.line1 == lastChange.line2) {
        endY += triangleGap;
      }

      if (startY <= y && endY > y) {
        result.addAll(block.ranges);
      }
      else if (lastLineSelected && line2 == visibleLineCount) {
        // special handling for deletion at the end of file
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

  @NotNull
  public static RangeHighlighter createTooltipRangeHighlighter(@NotNull Range range,
                                                               @NotNull MarkupModel markupModel) {
    TextRange textRange = DiffUtil.getLinesRange(markupModel.getDocument(), range.getLine1(), range.getLine2(), false);
    TextAttributes attributes = getTextAttributes(range);

    RangeHighlighter highlighter = markupModel.addRangeHighlighter(textRange.getStartOffset(), textRange.getEndOffset(),
                                                                   DiffDrawUtil.LST_LINE_MARKER_LAYER, attributes,
                                                                   HighlighterTargetArea.LINES_IN_RANGE);

    highlighter.setThinErrorStripeMark(true);
    highlighter.setGreedyToLeft(true);
    highlighter.setGreedyToRight(true);

    return highlighter;
  }

  @NotNull
  private static TextAttributes getTextAttributes(@NotNull Range range) {
    return new TextAttributes() {
      @Override
      public Color getErrorStripeColor() {
        return LineStatusMarkerRenderer.getErrorStripeColor(range);
      }
    };
  }

  //
  // Gutter painting
  //

  private Rectangle calcBounds(Editor editor, int lineNum, Rectangle bounds) {
    List<? extends Range> ranges = myTracker.getRanges();
    if (ranges == null) return null;

    List<ChangesBlock<Unit>> blocks = VisibleRangeMerger.merge(editor, ranges, bounds);
    if (blocks.isEmpty()) return null;

    int visibleLineCount = ((EditorImpl)editor).getVisibleLineCount();
    boolean lastLineSelected = lineNum == visibleLineCount - 1;

    ChangesBlock<Unit> lineBlock = null;
    for (ChangesBlock<Unit> block : blocks) {
      ChangedLines<Unit> firstChange = block.changes.get(0);
      ChangedLines<Unit> lastChange = block.changes.get(block.changes.size() - 1);

      int line1 = firstChange.line1;
      int line2 = lastChange.line2;

      int endLine = line1 == line2 ? line2 + 1 : line2;
      if (line1 <= lineNum && endLine > lineNum) {
        lineBlock = block;
        break;
      }
      if (lastLineSelected && line2 == visibleLineCount) {
        // special handling for deletion at the end of file
        lineBlock = block;
        break;
      }
      if (line1 > lineNum) break;
    }

    if (lineBlock == null) return null;

    List<ChangedLines<Unit>> changes = lineBlock.changes;
    int startLine = changes.get(0).line1;
    int endLine = changes.get(changes.size() - 1).line2;

    IntPair area = getGutterArea(editor);
    int y = editor.visualLineToY(startLine);
    int endY = editor.visualLineToY(endLine);
    return new Rectangle(area.first, y, area.second - area.first, endY - y);
  }

  protected boolean shouldPaintGutter() {
    return true;
  }

  protected boolean shouldPaintErrorStripeMarkers() {
    return shouldPaintGutter();
  }

  protected void paint(@NotNull Editor editor, @NotNull Graphics g) {
    paintDefault(editor, g, myTracker, DefaultFlagsProvider.DEFAULT, 0);
  }

  protected static void paintDefault(@NotNull Editor editor,
                                     @NotNull Graphics g,
                                     @NotNull LineStatusTrackerI<?> tracker,
                                     @NotNull FlagsProvider<DefaultLineFlags> flagsProvider,
                                     int framingBorder) {
    List<? extends Range> ranges = tracker.getRanges();
    if (ranges == null) return;

    List<ChangesBlock<DefaultLineFlags>> blocks = VisibleRangeMerger.merge(editor, ranges, flagsProvider, g.getClipBounds());
    for (ChangesBlock<DefaultLineFlags> block : blocks) {
      paintChangedLines((Graphics2D)g, editor, block.changes, framingBorder);
    }
  }

  protected static void paintChangedLines(@NotNull Graphics2D g,
                                          @NotNull Editor editor,
                                          @NotNull List<? extends ChangedLines<DefaultLineFlags>> block,
                                          int framingBorder) {
    EditorImpl editorImpl = (EditorImpl)editor;

    Color borderColor = getGutterBorderColor(editor);
    Color gutterBackgroundColor = ((EditorEx)editor).getGutterComponentEx().getBackground();

    int line1 = block.get(0).line1;
    int line2 = block.get(block.size() - 1).line2;

    IntPair area = getGutterArea(editor);
    final int x = area.first;
    final int endX = area.second;

    final int y = editorImpl.visualLineToY(line1);
    final int endY = editorImpl.visualLineToY(line2);


    if (framingBorder > 0) {
      if (y != endY) {
        g.setColor(gutterBackgroundColor);
        g.fillRect(x - framingBorder, y - framingBorder,
                   endX - x + framingBorder, endY - y + framingBorder * 2);
      }
    }

    for (ChangedLines<DefaultLineFlags> change : block) {
      if (change.line1 != change.line2 &&
          !change.flags.isIgnored) {
        int start = editorImpl.visualLineToY(change.line1);
        int end = editorImpl.visualLineToY(change.line2);

        Color gutterColor = getGutterColor(change.type, editor);
        paintRect(g, gutterColor, null, x, start, endX, end);
      }
    }

    if (borderColor == null) {
      for (ChangedLines<DefaultLineFlags> change : block) {
        if (change.line1 != change.line2 &&
            change.flags.isIgnored) {
          int start = editorImpl.visualLineToY(change.line1);
          int end = editorImpl.visualLineToY(change.line2);

          Color ignoredBorderColor = getIgnoredGutterBorderColor(change.type, editor);
          paintRect(g, null, ignoredBorderColor, x, start, endX, end);
        }
      }
    }
    else if (line1 != line2) {
      paintRect(g, null, borderColor, x, y, endX, endY);
    }

    for (ChangedLines<DefaultLineFlags> change : block) {
      if (change.line1 == change.line2) {
        int start = editorImpl.visualLineToY(change.line1);

        if (!change.flags.isIgnored) {
          Color gutterColor = getGutterColor(change.type, editor);
          paintTriangle(g, editor, gutterColor, borderColor, x, endX, start);
        }
        else if (borderColor != null) {
          paintTriangle(g, editor, null, borderColor, x, endX, start);
        }
        else {
          Color ignoredBorderColor = getIgnoredGutterBorderColor(change.type, editor);
          paintTriangle(g, editor, null, ignoredBorderColor, x, endX, start);
        }
      }
    }
  }

  public static void paintRange(@NotNull Graphics g,
                                @NotNull Editor editor,
                                @NotNull Range range,
                                int framingBorder,
                                boolean isIgnored) {
    FlagsProvider<DefaultLineFlags> flagsProvider = isIgnored ? DefaultFlagsProvider.ALL_IGNORED : DefaultFlagsProvider.DEFAULT;
    List<ChangesBlock<DefaultLineFlags>> blocks = VisibleRangeMerger.merge(editor, Collections.singletonList(range), flagsProvider,
                                                                           g.getClipBounds());
    for (ChangesBlock<DefaultLineFlags> block : blocks) {
      paintChangedLines((Graphics2D)g, editor, block.changes, framingBorder);
    }
  }

  public static void paintSimpleRange(Graphics g, Editor editor, int line1, int line2, @Nullable Color color) {
    IntPair horizontalArea = getGutterArea(editor);
    int x = horizontalArea.first;
    int endX = horizontalArea.second;

    int y = lineToY(editor, line1);
    int endY = lineToY(editor, line2);

    Color borderColor = getGutterBorderColor(editor);
    if (endY != y) {
      paintRect((Graphics2D)g, color, borderColor, x, y, endX, endY);
    }
    else {
      paintTriangle((Graphics2D)g, editor, color, borderColor, x, endX, y);
    }
  }

  @NotNull
  protected static IntPair getGutterArea(@NotNull Editor editor) {
    EditorGutterComponentEx gutter = ((EditorEx)editor).getGutterComponentEx();
    int x = gutter.getLineMarkerFreePaintersAreaOffset() + 1; // leave 1px for brace highlighters
    int endX = gutter.getWhitespaceSeparatorOffset();
    return new IntPair(x, endX);
  }

  public static boolean isInsideMarkerArea(@NotNull MouseEvent e) {
    final EditorGutterComponentEx gutter = (EditorGutterComponentEx)e.getComponent();
    return e.getX() > gutter.getLineMarkerFreePaintersAreaOffset();
  }

  protected static void paintRect(@NotNull Graphics2D g, @Nullable Color color, @Nullable Color borderColor,
                                  int x1, int y1, int x2, int y2) {
    if (color != null) {
      g.setColor(color);
      g.fillRect(x1, y1, x2 - x1, y2 - y1);
    }
    if (borderColor != null) {
      Stroke oldStroke = g.getStroke();
      g.setStroke(new BasicStroke(JBUIScale.scale(1)));
      g.setColor(borderColor);
      LinePainter2D.paint(g, x1, y1, x2 - 1, y1);
      LinePainter2D.paint(g, x1, y1, x1, y2 - 1);
      LinePainter2D.paint(g, x1, y2 - 1, x2 - 1, y2 - 1);
      g.setStroke(oldStroke);
    }
  }

  protected static void paintTriangle(@NotNull Graphics2D g, @NotNull Editor editor, @Nullable Color color, @Nullable Color borderColor,
                                      int x1, int x2, int y) {
    float editorScale = editor instanceof EditorImpl ? ((EditorImpl)editor).getScale() : 1.0f;
    int size = (int)JBUIScale.scale(4 * editorScale);
    if (y < size) y = size;

    final int[] xPoints = new int[]{x1, x1, x2};
    final int[] yPoints = new int[]{y - size, y + size, y};

    if (color != null) {
      g.setColor(color);
      g.fillPolygon(xPoints, yPoints, xPoints.length);
    }
    if (borderColor != null) {
      Stroke oldStroke = g.getStroke();
      g.setStroke(new BasicStroke(JBUIScale.scale(1)));
      g.setColor(borderColor);
      g.drawPolygon(xPoints, yPoints, xPoints.length);
      g.setStroke(oldStroke);
    }
  }

  @Nullable
  protected static Color getGutterColor(byte type, @Nullable Editor editor) {
    final EditorColorsScheme scheme = getColorScheme(editor);
    switch (type) {
      case Range.INSERTED:
        return scheme.getColor(EditorColors.ADDED_LINES_COLOR);
      case Range.DELETED:
        return scheme.getColor(EditorColors.DELETED_LINES_COLOR);
      case Range.MODIFIED:
        return scheme.getColor(EditorColors.MODIFIED_LINES_COLOR);
      case Range.EQUAL:
        return scheme.getColor(EditorColors.WHITESPACES_MODIFIED_LINES_COLOR);
      default:
        assert false;
        return null;
    }
  }

  @Nullable
  private static Color getErrorStripeColor(@NotNull Range range) {
    final EditorColorsScheme scheme = getColorScheme(null);
    switch (range.getType()) {
      case Range.INSERTED:
        return scheme.getAttributes(DiffColors.DIFF_INSERTED).getErrorStripeColor();
      case Range.DELETED:
        return scheme.getAttributes(DiffColors.DIFF_DELETED).getErrorStripeColor();
      case Range.MODIFIED:
        return scheme.getAttributes(DiffColors.DIFF_MODIFIED).getErrorStripeColor();
      default:
        assert false;
        return null;
    }
  }

  @Nullable
  private static Color getIgnoredGutterBorderColor(byte type, @Nullable Editor editor) {
    final EditorColorsScheme scheme = getColorScheme(editor);
    switch (type) {
      case Range.INSERTED:
        return scheme.getColor(EditorColors.IGNORED_ADDED_LINES_BORDER_COLOR);
      case Range.DELETED:
        return scheme.getColor(EditorColors.IGNORED_DELETED_LINES_BORDER_COLOR);
      case Range.MODIFIED:
      case Range.EQUAL:
        return scheme.getColor(EditorColors.IGNORED_MODIFIED_LINES_BORDER_COLOR);
      default:
        assert false;
        return null;
    }
  }

  @Nullable
  private static Color getGutterBorderColor(@Nullable Editor editor) {
    return getColorScheme(editor).getColor(EditorColors.BORDER_LINES_COLOR);
  }

  @NotNull
  private static EditorColorsScheme getColorScheme(@Nullable Editor editor) {
    return editor != null ? editor.getColorsScheme() : EditorColorsManager.getInstance().getGlobalScheme();
  }


  public static abstract class DefaultFlagsProvider implements FlagsProvider<DefaultLineFlags> {
    public static final FlagsProvider<DefaultLineFlags> DEFAULT = new DefaultFlagsProvider() {
      @Override
      public @NotNull DefaultLineFlags getFlags(@NotNull Range range) {
        return DefaultLineFlags.DEFAULT;
      }
    };

    public static final FlagsProvider<DefaultLineFlags> ALL_IGNORED = new DefaultFlagsProvider() {
      @Override
      public @NotNull DefaultLineFlags getFlags(@NotNull Range range) {
        return DefaultLineFlags.IGNORED;
      }
    };

    @Override
    public @NotNull DefaultLineFlags mergeFlags(@NotNull DefaultLineFlags flags1, @NotNull DefaultLineFlags flags2) {
      return flags1.isIgnored && flags2.isIgnored ? DefaultLineFlags.IGNORED : DefaultLineFlags.DEFAULT;
    }

    @Override
    public boolean shouldIgnoreInnerRanges(@NotNull DefaultLineFlags flag) {
      return flag.isIgnored;
    }
  }

  public static class DefaultLineFlags {
    public static final DefaultLineFlags DEFAULT = new DefaultLineFlags(false);
    public static final DefaultLineFlags IGNORED = new DefaultLineFlags(true);

    public boolean isIgnored;

    private DefaultLineFlags(boolean isIgnored) {
      this.isIgnored = isIgnored;
    }
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
