// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.ex;

import com.intellij.diff.util.DiffDrawUtil;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.IntPair;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
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
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.intellij.diff.util.DiffDrawUtil.lineToY;
import static com.intellij.diff.util.DiffDrawUtil.yToLine;
import static com.intellij.diff.util.DiffUtil.getLineCount;
import static com.intellij.openapi.diagnostic.Logger.getInstance;
import static com.intellij.util.ui.update.MergingUpdateQueue.ANY_COMPONENT;
import static java.util.Collections.emptyList;

public abstract class LineStatusMarkerRenderer {
  private static final Logger LOG = getInstance(LineStatusMarkerRenderer.class);

  @NotNull protected final LineStatusTrackerBase<?> myTracker;
  private final MarkupEditorFilter myEditorFilter;

  @NotNull private final MergingUpdateQueue myUpdateQueue;
  private boolean myDisposed = false;
  @NotNull private final RangeHighlighter myHighlighter;
  @NotNull private final List<RangeHighlighter> myTooltipHighlighters = new ArrayList<>();

  public LineStatusMarkerRenderer(@NotNull LineStatusTrackerBase<?> tracker) {
    myTracker = tracker;
    myEditorFilter = getEditorFilter();

    Document document = myTracker.getDocument();
    MarkupModel markupModel = DocumentMarkupModel.forDocument(document, myTracker.getProject(), true);
    myHighlighter = markupModel.addRangeHighlighter(0, document.getTextLength(), DiffDrawUtil.LST_LINE_MARKER_LAYER, null,
                                                    HighlighterTargetArea.LINES_IN_RANGE);
    myHighlighter.setGreedyToLeft(true);
    myHighlighter.setGreedyToRight(true);

    myHighlighter.setLineMarkerRenderer(new MyActiveGutterRenderer());

    if (myEditorFilter != null) myHighlighter.setEditorFilter(myEditorFilter);

    myUpdateQueue = new MergingUpdateQueue("LineStatusMarkerRenderer", 100, true, ANY_COMPONENT, myTracker.getDisposable());

    Disposer.register(myTracker.getDisposable(), new Disposable() {
      @Override
      public void dispose() {
        myDisposed = true;
        destroyHighlighters();
      }
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

    for (RangeHighlighter highlighter: myTooltipHighlighters) {
      disposeHighlighter(highlighter);
    }
    myTooltipHighlighters.clear();

    List<? extends Range> ranges = myTracker.getRanges();
    if (ranges != null) {
      MarkupModel markupModel = DocumentMarkupModel.forDocument(myTracker.getDocument(), myTracker.getProject(), true);
      for (Range range: ranges) {
        RangeHighlighter highlighter = createTooltipRangeHighlighter(range, markupModel);
        if (myEditorFilter != null) highlighter.setEditorFilter(myEditorFilter);
        myTooltipHighlighters.add(highlighter);
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
  protected List<? extends Range> getSelectedRanges(@NotNull Editor editor, int y) {
    List<? extends Range> ranges = myTracker.getRanges();
    if (ranges == null) return emptyList();

    int lineHeight = editor.getLineHeight();
    int visibleLineCount = ((EditorImpl)editor).getVisibleLineCount();
    boolean lastLineSelected = editor.yToVisualLine(y) == visibleLineCount - 1;
    int triangleGap = lineHeight / 3;

    Rectangle clip = new Rectangle(0, y - lineHeight, editor.getComponent().getWidth(), lineHeight * 2);
    List<ChangesBlock> blocks = createMerger(editor).run(ranges, clip);

    List<Range> result = new ArrayList<>();
    for (ChangesBlock block : blocks) {
      ChangedLines firstChange = block.changes.get(0);
      ChangedLines lastChange = block.changes.get(block.changes.size() - 1);

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

  @NotNull
  protected VisibleRangeMerger createMerger(@NotNull Editor editor) {
    return new VisibleRangeMerger(editor);
  }

  @Nullable
  protected MarkupEditorFilter getEditorFilter() {
    return null;
  }

  protected int getFramingBorderSize() {
    return 0;
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
        return LineStatusMarkerRenderer.getErrorStripeColor(range, null);
      }
    };
  }

  //
  // Gutter painting
  //

  private Rectangle calcBounds(Editor editor, int lineNum, Rectangle bounds) {
    List<? extends Range> ranges = myTracker.getRanges();
    if (ranges == null) return null;

    List<ChangesBlock> blocks = createMerger(editor).run(ranges, bounds);
    if (blocks.isEmpty()) return null;

    int visibleLineCount = ((EditorImpl)editor).getVisibleLineCount();
    boolean lastLineSelected = lineNum == visibleLineCount - 1;

    ChangesBlock lineBlock = null;
    for (ChangesBlock block : blocks) {
      ChangedLines firstChange = block.changes.get(0);
      ChangedLines lastChange = block.changes.get(block.changes.size() - 1);

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

    List<ChangedLines> changes = lineBlock.changes;
    int startLine = changes.get(0).line1;
    int endLine = changes.get(changes.size() - 1).line2;

    IntPair area = getGutterArea(editor);
    int y = editor.visualLineToY(startLine);
    int endY = editor.visualLineToY(endLine);
    return new Rectangle(area.val1, y, area.val2 - area.val1, endY - y);
  }

  protected void paint(@NotNull Editor editor, @NotNull Graphics g) {
    List<? extends Range> ranges = myTracker.getRanges();
    if (ranges == null) return;

    int framingBorder = getFramingBorderSize();

    List<ChangesBlock> blocks = createMerger(editor).run(ranges, g.getClipBounds());
    for (ChangesBlock block : blocks) {
      paintChangedLines((Graphics2D)g, editor, block.changes, framingBorder);
    }
  }

  private static void paintChangedLines(@NotNull Graphics2D g,
                                        @NotNull Editor editor,
                                        @NotNull List<ChangedLines> block,
                                        int framingBorder) {
    EditorImpl editorImpl = (EditorImpl)editor;

    Color borderColor = getGutterBorderColor(editor);
    Color gutterBackgroundColor = ((EditorEx)editor).getGutterComponentEx().getBackground();

    int line1 = block.get(0).line1;
    int line2 = block.get(block.size() - 1).line2;

    IntPair area = getGutterArea(editor);
    final int x = area.val1;
    final int endX = area.val2;

    final int y = editorImpl.visualLineToY(line1);
    final int endY = editorImpl.visualLineToY(line2);


    if (framingBorder > 0) {
      if (y != endY) {
        g.setColor(gutterBackgroundColor);
        g.fillRect(x - framingBorder, y - framingBorder,
                   endX - x + framingBorder, endY - y + framingBorder * 2);
      }
    }

    for (ChangedLines change: block) {
      if (change.line1 != change.line2 &&
          !change.isIgnored) {
        int start = editorImpl.visualLineToY(change.line1);
        int end = editorImpl.visualLineToY(change.line2);

        Color gutterColor = getGutterColor(change.type, editor);
        paintRect(g, gutterColor, null, x, start, endX, end);
      }
    }

    if (borderColor == null) {
      for (ChangedLines change: block) {
        if (change.line1 != change.line2 &&
            change.isIgnored) {
          int start = editorImpl.visualLineToY(change.line1);
          int end = editorImpl.visualLineToY(change.line2);

          Color ignoredBorderColor = getIgnoredGutterBorderColor(change.type, editor);
          paintRect(g, null, ignoredBorderColor, x, start, endX, end);
        }
      }
    }
    else {
      paintRect(g, null, borderColor, x, y, endX, endY);
    }

    for (ChangedLines change: block) {
      if (change.line1 == change.line2) {
        int start = editorImpl.visualLineToY(change.line1);

        if (!change.isIgnored) {
          Color gutterColor = getGutterColor(change.type, editor);
          paintTriangle(g, editor, gutterColor, borderColor, x, endX, start);
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
                                int framingBorder) {
    List<ChangesBlock> blocks = new VisibleRangeMerger(editor).run(Collections.singletonList(range), g.getClipBounds());
    for (ChangesBlock block : blocks) {
      paintChangedLines((Graphics2D)g, editor, block.changes, framingBorder);
    }
  }

  public static void paintSimpleRange(Graphics g, Editor editor, int line1, int line2, @Nullable Color color) {
    IntPair horizontalArea = getGutterArea(editor);
    int x = horizontalArea.val1;
    int endX = horizontalArea.val2;

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
  private static IntPair getGutterArea(@NotNull Editor editor) {
    EditorGutterComponentEx gutter = ((EditorEx)editor).getGutterComponentEx();
    int x = gutter.getLineMarkerFreePaintersAreaOffset() + 1; // leave 1px for brace highlighters
    int endX = gutter.getWhitespaceSeparatorOffset();
    return new IntPair(x, endX);
  }

  public static boolean isInsideMarkerArea(@NotNull MouseEvent e) {
    final EditorGutterComponentEx gutter = (EditorGutterComponentEx)e.getComponent();
    return e.getX() > gutter.getLineMarkerFreePaintersAreaOffset();
  }

  private static void paintRect(@NotNull Graphics2D g, @Nullable Color color, @Nullable Color borderColor, int x1, int y1, int x2, int y2) {
    if (color != null) {
      g.setColor(color);
      g.fillRect(x1, y1, x2 - x1, y2 - y1);
    }
    if (borderColor != null) {
      Stroke oldStroke = g.getStroke();
      g.setStroke(new BasicStroke(JBUIScale.scale(1)));
      g.setColor(borderColor);
      UIUtil.drawLine(g, x1, y1, x2 - 1, y1);
      UIUtil.drawLine(g, x1, y1, x1, y2 - 1);
      UIUtil.drawLine(g, x1, y2 - 1, x2 - 1, y2 - 1);
      g.setStroke(oldStroke);
    }
  }

  private static void paintTriangle(@NotNull Graphics2D g, @NotNull Editor editor, @Nullable Color color, @Nullable Color borderColor,
                                    int x1, int x2, int y) {
    float editorScale = editor instanceof EditorImpl ? ((EditorImpl)editor).getScale() : 1.0f;
    int size = (int)JBUIScale.scale(4 * editorScale);

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
  private static Color getGutterColor(byte type, @Nullable Editor editor) {
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
  private static Color getErrorStripeColor(@NotNull Range range, @Nullable Editor editor) {
    final EditorColorsScheme scheme = getColorScheme(editor);
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
    Color borderColor = getGutterBorderColor(editor);
    if (borderColor != null) return borderColor;

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


  protected static class VisibleRangeMerger {
    @NotNull private final Editor myEditor;

    @NotNull private ChangesBlock myBlock = new ChangesBlock();

    @NotNull private final List<ChangesBlock> myResult = new ArrayList<>();

    public VisibleRangeMerger(@NotNull Editor editor) {
      myEditor = editor;
    }

    protected boolean isIgnored(@NotNull Range range) {
      return false;
    }

    @NotNull
    public List<ChangesBlock> run(@NotNull List<? extends Range> ranges, @NotNull Rectangle clip) {
      int visibleLineStart = yToLine(myEditor, clip.y);
      int visibleLineEnd = yToLine(myEditor, clip.y + clip.height) + 1;

      for (Range range: ranges) {
        int line1 = range.getLine1();
        int line2 = range.getLine2();

        if (line2 < visibleLineStart) continue;
        if (line1 > visibleLineEnd) break;

        boolean isIgnored = isIgnored(range);
        List<Range.InnerRange> innerRanges = range.getInnerRanges();

        if (innerRanges == null || isIgnored) {
          processLine(range, line1, line2, range.getType(), isIgnored);
        }
        else {
          for (Range.InnerRange innerRange: innerRanges) {
            int innerLine1 = line1 + innerRange.getLine1();
            int innerLine2 = line1 + innerRange.getLine2();
            byte innerType = innerRange.getType();

            processLine(range, innerLine1, innerLine2, innerType, isIgnored);
          }
        }
      }

      finishBlock();
      return myResult;
    }

    private void processLine(@NotNull Range range, int start, int end, byte type, boolean isIgnored) {
      EditorImpl editorImpl = (EditorImpl)myEditor;
      Document document = myEditor.getDocument();
      int lineCount = getLineCount(document);

      int visualStart;
      boolean startHasFolding;
      if (start < lineCount) {
        int startOffset = document.getLineStartOffset(start);
        visualStart = editorImpl.offsetToVisualLine(startOffset);
        startHasFolding = startOffset > 0 && myEditor.getFoldingModel().isOffsetCollapsed(startOffset - 1);
      }
      else {
        LOG.assertTrue(start == lineCount);
        int lastVisualLine = editorImpl.offsetToVisualLine(document.getTextLength());
        visualStart = lastVisualLine + start - lineCount + 1;
        startHasFolding = false;
      }

      if (start == end) {
        if (startHasFolding) {
          appendChange(range, new ChangedLines(visualStart, visualStart + 1, Range.MODIFIED, isIgnored));
        }
        else {
          appendChange(range, new ChangedLines(visualStart, visualStart, type, isIgnored));
        }
      }
      else {
        int visualEnd;
        boolean endHasFolding;
        if (end < lineCount) {
          int endOffset = document.getLineEndOffset(end - 1);
          visualEnd = editorImpl.offsetToVisualLine(endOffset) + 1;
          endHasFolding = myEditor.getFoldingModel().isOffsetCollapsed(endOffset);
        }
        else {
          LOG.assertTrue(end == lineCount);
          int lastVisualLine = editorImpl.offsetToVisualLine(document.getTextLength());
          visualEnd = lastVisualLine + end - lineCount + 1;
          endHasFolding = false;
        }

        if (type == Range.EQUAL || type == Range.MODIFIED) {
          appendChange(range, new ChangedLines(visualStart, visualEnd, type, isIgnored));
        }
        else {
          if (startHasFolding && visualEnd - visualStart > 1) {
            appendChange(range, new ChangedLines(visualStart, visualStart + 1, Range.MODIFIED, isIgnored));
            startHasFolding = false;
            visualStart++;
          }
          if (endHasFolding && visualEnd - visualStart > 1) {
            appendChange(range, new ChangedLines(visualStart, visualEnd - 1, type, isIgnored));
            appendChange(range, new ChangedLines(visualEnd - 1, visualEnd, Range.MODIFIED, isIgnored));
          }
          else {
            byte bodyType = startHasFolding || endHasFolding ? Range.MODIFIED : type;
            appendChange(range, new ChangedLines(visualStart, visualEnd, bodyType, isIgnored));
          }
        }
      }
    }

    private void appendChange(@NotNull Range range, @NotNull ChangedLines newChange) {
      ChangedLines lastItem = ContainerUtil.getLastItem(myBlock.changes);
      if (lastItem != null && lastItem.line2 < newChange.line1) {
        finishBlock();
      }

      List<ChangedLines> changes = myBlock.changes;
      List<Range> ranges = myBlock.ranges;

      if (ContainerUtil.getLastItem(ranges) != range) {
        ranges.add(range);
      }

      if (changes.isEmpty()) {
        changes.add(newChange);
        return;
      }

      ChangedLines lastChange = changes.remove(changes.size() - 1);

      if (lastChange.line1 == lastChange.line2 &&
          newChange.line1 == newChange.line2) {
        assert lastChange.line1 == newChange.line1;
        byte type = lastChange.type == newChange.type ? lastChange.type : Range.MODIFIED;
        boolean isIgnored = lastChange.isIgnored && newChange.isIgnored;
        changes.add(new ChangedLines(lastChange.line1, lastChange.line2, type, isIgnored));
      }
      else if (lastChange.line1 == lastChange.line2 && newChange.type == Range.EQUAL ||
               newChange.line1 == newChange.line2 && lastChange.type == Range.EQUAL) {
        changes.add(lastChange);
        changes.add(newChange);
      }
      else if (lastChange.type == newChange.type &&
               lastChange.isIgnored == newChange.isIgnored) {
        int union1 = Math.min(lastChange.line1, newChange.line1);
        int union2 = Math.max(lastChange.line2, newChange.line2);
        changes.add(new ChangedLines(union1, union2, lastChange.type, lastChange.isIgnored));
      }
      else {
        int intersection1 = Math.max(lastChange.line1, newChange.line1);
        int intersection2 = Math.min(lastChange.line2, newChange.line2);

        if (lastChange.line1 != intersection1) {
          changes.add(new ChangedLines(lastChange.line1, intersection1, lastChange.type, lastChange.isIgnored));
        }

        if (intersection1 != intersection2) {
          byte type = lastChange.type == newChange.type ? lastChange.type : Range.MODIFIED;
          boolean isIgnored = lastChange.isIgnored && newChange.isIgnored;
          changes.add(new ChangedLines(intersection1, intersection2, type, isIgnored));
        }

        if (newChange.line2 != intersection2) {
          changes.add(new ChangedLines(intersection2, newChange.line2, newChange.type, newChange.isIgnored));
        }
      }
    }

    private void finishBlock() {
      if (myBlock.changes.isEmpty()) return;
      myResult.add(myBlock);
      myBlock = new ChangesBlock();
    }
  }

  private static class ChangesBlock {
    @NotNull public final List<ChangedLines> changes = new ArrayList<>();
    @NotNull public final List<Range> ranges = new ArrayList<>();
  }

  private static class ChangedLines {
    // VisualPosition.line
    public final int line1;
    public final int line2;
    public final byte type;
    private final boolean isIgnored;

    ChangedLines(int line1, int line2, byte type, boolean isIgnored) {
      this.line1 = line1;
      this.line2 = line2;
      this.type = type;
      this.isIgnored = isIgnored;
    }
  }


  private class MyActiveGutterRenderer implements ActiveGutterRenderer {
    @Override
    public void paint(Editor editor, Graphics g, Rectangle r) {
      LineStatusMarkerRenderer.this.paint(editor, g);
    }

    @Override
    public boolean canDoAction(@NotNull Editor editor, @NotNull MouseEvent e) {
      return LineStatusMarkerRenderer.this.canDoAction(editor, e);
    }

    @Override
    public void doAction(@NotNull Editor editor, @NotNull MouseEvent e) {
      LineStatusMarkerRenderer.this.doAction(editor, e);
    }

    @Nullable
    @Override
    public Rectangle calcBounds(@NotNull Editor editor, int lineNum, @NotNull Rectangle preferredBounds) {
      return LineStatusMarkerRenderer.this.calcBounds(editor, lineNum, preferredBounds);
    }

    @NotNull
    @Override
    public String getAccessibleName() {
      return "VCS marker: changed line";
    }
  }
}
