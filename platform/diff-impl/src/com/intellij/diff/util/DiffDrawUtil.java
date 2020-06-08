// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.util;

import com.intellij.codeInsight.folding.impl.FoldingUtil;
import com.intellij.diff.fragments.DiffFragment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.util.BooleanGetter;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.ui.JBColor;
import com.intellij.ui.paint.PaintUtil;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.DocumentUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Path2D;
import java.util.List;
import java.util.*;

import static com.intellij.diff.util.DiffUtil.getLineCount;

public final class DiffDrawUtil {
  private static final Logger LOG = Logger.getInstance(DiffDrawUtil.class);

  public static final int STRIPE_LAYER = HighlighterLayer.ERROR - 1;
  public static final int DEFAULT_LAYER = HighlighterLayer.SELECTION - 102;
  public static final int INLINE_LAYER = HighlighterLayer.SELECTION - 101;
  public static final int LINE_MARKER_LAYER = HighlighterLayer.SELECTION - 100;
  public static final int LST_LINE_MARKER_LAYER = HighlighterLayer.SELECTION - 1;

  private static final double CTRL_PROXIMITY_X = 0.3;

  public static final LineSeparatorRenderer BORDER_LINE_RENDERER = new LineSeparatorRenderer() {
    @Override
    public void drawLine(Graphics g, int x1, int x2, int y) {
      Rectangle clip = g.getClipBounds();
      x2 = clip.x + clip.width;
      g.setColor(JBColor.border());
      g.drawLine(x1, y, x2, y);
    }
  };

  private DiffDrawUtil() {
  }

  @NotNull
  public static Color getDividerColor() {
    return getDividerColor(null);
  }

  @NotNull
  public static Color getDividerColor(@Nullable Editor editor) {
    return getDividerColorFromScheme(editor != null ? editor.getColorsScheme() : EditorColorsManager.getInstance().getGlobalScheme());
  }

  @NotNull
  public static Color getDividerColorFromScheme(@NotNull EditorColorsScheme scheme) {
    Color gutterBackground = scheme.getColor(EditorColors.GUTTER_BACKGROUND);
    if (gutterBackground == null) {
      gutterBackground = EditorColors.GUTTER_BACKGROUND.getDefaultColor();
    }
    return gutterBackground;
  }

  public static void drawChunkBorderLine(@NotNull Graphics2D g, int x1, int x2, int y, @NotNull Color color,
                                         boolean doubleLine, boolean dottedLine) {
    if (dottedLine && doubleLine) {
      UIUtil.drawBoldDottedLine(g, x1, x2, y - 1, null, color, false);
      UIUtil.drawBoldDottedLine(g, x1, x2, y, null, color, false);
    }
    else if (dottedLine) {
      UIUtil.drawBoldDottedLine(g, x1, x2, y - 1, null, color, false);
    }
    else if (doubleLine) {
      UIUtil.drawLine(g, x1, y, x2, y, null, color);
      UIUtil.drawLine(g, x1, y + 1, x2, y + 1, null, color);
    }
    else {
      UIUtil.drawLine(g, x1, y, x2, y, null, color);
    }
  }

  public static void drawTrapezium(@NotNull Graphics2D g,
                                   int x1, int x2,
                                   int start1, int end1,
                                   int start2, int end2,
                                   @Nullable Color fillColor,
                                   @Nullable Color borderColor) {
    if (fillColor != null) {
      final int[] xPoints = new int[]{x1, x2, x2, x1};
      final int[] yPoints = new int[]{start1, start2, end2 + 1, end1 + 1};

      g.setColor(fillColor);
      g.fillPolygon(xPoints, yPoints, xPoints.length);
    }

    if (borderColor != null) {
      g.setColor(borderColor);
      g.drawLine(x1, start1, x2, start2);
      g.drawLine(x1, end1, x2, end2);
    }
  }

  public static void drawCurveTrapezium(@NotNull Graphics2D g,
                                        int x1, int x2,
                                        int start1, int end1,
                                        int start2, int end2,
                                        @Nullable Color fillColor,
                                        @Nullable Color borderColor) {
    if (fillColor != null) {
      g.setColor(fillColor);
      g.fill(makeCurvePath(x1, x2, start1, start2, end1 + 1, end2 + 1));

      // 'g.fill' above draws thin line when used with high slopes. Here we ensure that background is never less than 1px thick.
      Stroke oldStroke = g.getStroke();
      g.setStroke(new BasicStroke(JBUIScale.scale(1f)));
      g.draw(makeCurve(x1, x2, (start1 + end1) / 2, (start2 + end2) / 2, true));
      g.setStroke(oldStroke);
    }

    if (borderColor != null) {
      g.setColor(borderColor);
      drawCurveLine(g, x1, x2, start1, start2);
      drawCurveLine(g, x1, x2, end1, end2);
    }
  }

  /**
   * {@link Graphics2D#fill} uses different aliasing than {@link Graphics2D#draw}.
   * We want this curve to look similar to {@link #drawChunkBorderLine}, that is using {@link com.intellij.ui.paint.LinePainter2D}.
   * Here we mock a hack from LinePainter2D, using 'fill' instead of 'draw' to draw a line.
   * <p>
   * It's hard to build 'parallel curve' for a given cubic curve.
   * We're using a simple approach that looks OK for almost-horizontal lines,
   * when the difference between 'draw' and 'fill' is most noticeable.
   */
  private static void drawCurveLine(@NotNull Graphics2D g, int x1, int x2, int y1, int y2) {
    boolean isHighSlope = Math.abs(x2 - x1) < Math.abs(y2 - y1);
    if (!isHighSlope && isThickSimpleStroke(g)) {
      g.fill(makeCurvePath(x1, x2, y1, y2, y1 + 1, y2 + 1));
    }
    else {
      g.draw(makeCurve(x1, x2, y1, y2, true));
    }
  }

  private static boolean isThickSimpleStroke(@NotNull Graphics2D g) {
    Stroke stroke = g.getStroke();
    if (stroke instanceof BasicStroke) {
      float strokeWidth = ((BasicStroke)stroke).getLineWidth();
      return strokeWidth == 1.0 && PaintUtil.devValue(strokeWidth, g) > 1;
    }
    return false;
  }

  @NotNull
  private static Path2D makeCurvePath(int x1, int x2,
                                      int y11, int y12, int y21, int y22) {
    Path2D path = new Path2D.Double();
    path.append(makeCurve(x1, x2, y11, y12, true), true);
    path.append(makeCurve(x1, x2, y21, y22, false), true);
    path.closePath();
    return path;
  }

  private static Shape makeCurve(int x1, int x2, int y1, int y2, boolean forward) {
    int width = x2 - x1;
    if (forward) {
      return new CubicCurve2D.Double(x1, y1,
                                     x1 + width * CTRL_PROXIMITY_X, y1,
                                     x1 + width * (1.0 - CTRL_PROXIMITY_X), y2,
                                     x1 + width, y2);
    }
    else {
      return new CubicCurve2D.Double(x1 + width, y2,
                                     x1 + width * (1.0 - CTRL_PROXIMITY_X), y2,
                                     x1 + width * CTRL_PROXIMITY_X, y1,
                                     x1, y1);
    }
  }

  //
  // Impl
  //

  public static int lineToY(@NotNull Editor editor, int line) {
    return lineToY(editor, line, true, false);
  }

  public static int lineToY(@NotNull Editor editor, int line, boolean lineStart) {
    return lineToY(editor, line, lineStart, false);
  }

  public static int lineToY(@NotNull Editor editor, int line, boolean lineStart, boolean includeInlays) {
    if (line < 0) return 0;

    Document document = editor.getDocument();
    if (line >= getLineCount(document)) {
      int y = editor.logicalPositionToXY(editor.offsetToLogicalPosition(document.getTextLength())).y;
      int tailLines = line - getLineCount(document) + (lineStart ? 0 : 1);
      return y + editor.getLineHeight() * tailLines;
    }

    if (lineStart) {
      int visualLine = editor.offsetToVisualPosition(document.getLineStartOffset(line), false, false).line;
      int inlay = includeInlays ? EditorUtil.getInlaysHeight(editor, visualLine, true) : 0;
      return editor.visualLineToY(visualLine) - inlay;
    }
    else {
      int visualLine = editor.offsetToVisualPosition(document.getLineEndOffset(line), true, true).line;
      int inlay = includeInlays ? EditorUtil.getInlaysHeight(editor, visualLine, false) : 0;
      return editor.visualLineToY(visualLine) + editor.getLineHeight() + inlay;
    }
  }

  public static int yToLine(@NotNull Editor editor, int y) {
    return editor.xyToLogicalPosition(new Point(0, y)).line;
  }

  @NotNull
  public static MarkerRange getGutterMarkerPaintRange(@NotNull Editor editor, int startLine, int endLine) {
    int y1;
    int y2;
    if (startLine == endLine) {
      if (startLine == 0) {
        y1 = lineToY(editor, 0, true, true) + 1;
      }
      else {
        y1 = lineToY(editor, startLine - 1, false, true);
      }
      y2 = y1;
    }
    else {
      y1 = lineToY(editor, startLine, true, false);
      y2 = lineToY(editor, endLine - 1, false, false);
    }
    return new MarkerRange(y1, y2);
  }

  @Nullable
  private static TextAttributes getTextAttributes(@NotNull final TextDiffType type,
                                                  @Nullable final Editor editor,
                                                  @NotNull BackgroundType background) {
    if (background == BackgroundType.NONE) return null;
    return new TextAttributes() {
      @Override
      public Color getBackgroundColor() {
        return background == BackgroundType.IGNORED ? type.getIgnoredColor(editor) : type.getColor(editor);
      }
    };
  }

  @NotNull
  private static TextAttributes getStripeTextAttributes(@NotNull final TextDiffType type,
                                                        @NotNull final Editor editor) {
    return new TextAttributes() {
      @Override
      public Color getErrorStripeColor() {
        return type.getMarkerColor(editor);
      }
    };
  }

  private static void installEmptyRangeRenderer(@NotNull RangeHighlighter highlighter,
                                                @NotNull TextDiffType type) {
    highlighter.setCustomRenderer(new DiffEmptyHighlighterRenderer(type));
  }

  @NotNull
  private static LineSeparatorRenderer createDiffLineRenderer(@NotNull Editor editor,
                                                              @Nullable RangeHighlighter parentHighlighter,
                                                              @NotNull TextDiffType type,
                                                              @NotNull SeparatorPlacement placement,
                                                              final boolean doubleLine,
                                                              final boolean resolved) {
    return new LineSeparatorRenderer() {
      @Override
      public void drawLine(Graphics g, int x1, int x2, int y) {
        if (parentHighlighter != null && FoldingUtil.isHighlighterFolded(editor, parentHighlighter)) return;
        Rectangle clip = g.getClipBounds();
        x2 = clip.x + clip.width;
        if (placement == SeparatorPlacement.TOP) y++;
        drawChunkBorderLine((Graphics2D)g, x1, x2, y, type.getColor(editor), doubleLine, resolved);
      }
    };
  }

  @NotNull
  private static LineMarkerRenderer createFoldingGutterLineRenderer(@NotNull final TextDiffType type,
                                                                    @NotNull final SeparatorPlacement placement,
                                                                    final boolean doubleLine,
                                                                    final boolean resolved) {
    return new LineMarkerRendererEx() {
      @Override
      public void paint(@NotNull Editor editor, @NotNull Graphics g, @NotNull Rectangle r) {
        EditorGutterComponentEx gutter = ((EditorEx)editor).getGutterComponentEx();
        Graphics2D g2 = (Graphics2D)g;

        int x1 = gutter.getWhitespaceSeparatorOffset();
        int x2 = gutter.getWidth();

        int y = r.y;
        if (placement == SeparatorPlacement.BOTTOM) {
          LOG.warn("BOTTOM gutter line renderers are not supported");
          y += editor.getLineHeight() - 1;
        }

        drawChunkBorderLine(g2, x1, x2, y, type.getColor(editor), doubleLine, resolved);
      }

      @NotNull
      @Override
      public Position getPosition() {
        return Position.CUSTOM;
      }
    };
  }

  //
  // Highlighters
  //

  // TODO: desync of range and 'border' line markers on typing

  @NotNull
  public static List<RangeHighlighter> createUnifiedChunkHighlighters(@NotNull Editor editor,
                                                                      @NotNull LineRange deleted,
                                                                      @NotNull LineRange inserted,
                                                                      @Nullable List<? extends DiffFragment> innerFragments) {
    return createUnifiedChunkHighlighters(editor, deleted, inserted, false, false, innerFragments);
  }

  @NotNull
  public static List<RangeHighlighter> createUnifiedChunkHighlighters(@NotNull Editor editor,
                                                                      @NotNull LineRange deleted,
                                                                      @NotNull LineRange inserted,
                                                                      boolean excluded,
                                                                      boolean skipped,
                                                                      @Nullable List<? extends DiffFragment> innerFragments) {
    boolean ignored = innerFragments != null;

    List<RangeHighlighter> list = new ArrayList<>();
    if (!inserted.isEmpty() && !deleted.isEmpty()) {
      list.addAll(createHighlighter(editor, deleted.start, deleted.end, TextDiffType.DELETED, ignored, skipped, excluded));
      list.addAll(createHighlighter(editor, inserted.start, inserted.end, TextDiffType.INSERTED, ignored, skipped, excluded));
    }
    else if (!inserted.isEmpty()) {
      list.addAll(createHighlighter(editor, inserted.start, inserted.end, TextDiffType.INSERTED, ignored, skipped, excluded));
    }
    else if (!deleted.isEmpty()) {
      list.addAll(createHighlighter(editor, deleted.start, deleted.end, TextDiffType.DELETED, ignored, skipped, excluded));
    }

    if (innerFragments != null && !skipped) {
      int deletedStartOffset = editor.getDocument().getLineStartOffset(deleted.start);
      int insertedStartOffset = editor.getDocument().getLineStartOffset(inserted.start);

      for (DiffFragment fragment : innerFragments) {
        int deletedWordStart = deletedStartOffset + fragment.getStartOffset1();
        int deletedWordEnd = deletedStartOffset + fragment.getEndOffset1();
        list.addAll(createInlineHighlighter(editor, deletedWordStart, deletedWordEnd, TextDiffType.DELETED));

        int insertedWordStart = insertedStartOffset + fragment.getStartOffset2();
        int insertedWordEnd = insertedStartOffset + fragment.getEndOffset2();
        list.addAll(createInlineHighlighter(editor, insertedWordStart, insertedWordEnd, TextDiffType.INSERTED));
      }
    }

    return list;
  }

  @NotNull
  private static List<RangeHighlighter> createHighlighter(@NotNull Editor editor, int startLine, int endLine, @NotNull TextDiffType type,
                                                          boolean ignored, boolean excludedInEditor, boolean excludedInGutter) {
    return new LineHighlighterBuilder(editor, startLine, endLine, type)
      .withIgnored(ignored)
      .withExcludedInEditor(excludedInEditor)
      .withExcludedInGutter(excludedInGutter)
      .done();
  }

  @NotNull
  public static List<RangeHighlighter> createHighlighter(@NotNull Editor editor, int startLine, int endLine, @NotNull TextDiffType type,
                                                         boolean ignored) {
    return new LineHighlighterBuilder(editor, startLine, endLine, type).withIgnored(ignored).done();
  }

  @NotNull
  public static List<RangeHighlighter> createHighlighter(@NotNull Editor editor, int startLine, int endLine, @NotNull TextDiffType type,
                                                         boolean ignored,
                                                         boolean resolved,
                                                         boolean isExcluded,
                                                         boolean hideWithoutLineNumbers,
                                                         boolean hideStripeMarkers) {
    return new LineHighlighterBuilder(editor, startLine, endLine, type)
      .withIgnored(ignored)
      .withResolved(resolved)
      .withExcluded(isExcluded)
      .withHideWithoutLineNumbers(hideWithoutLineNumbers)
      .withHideStripeMarkers(hideStripeMarkers)
      .done();
  }

  @NotNull
  public static List<RangeHighlighter> createInlineHighlighter(@NotNull Editor editor, int start, int end, @NotNull TextDiffType type) {
    return new InlineHighlighterBuilder(editor, start, end, type).done();
  }

  @NotNull
  public static List<RangeHighlighter> createLineMarker(@NotNull final Editor editor, int line, @NotNull final TextDiffType type) {
    if (line == 0) return Collections.emptyList();
    return new LineMarkerBuilder(editor, line, SeparatorPlacement.TOP)
      .withDefaultRenderer(type, false, false, null)
      .withDefaultGutterRenderer(type, false, false)
      .withDefaultStripeAttributes(type)
      .done();
  }

  @NotNull
  public static List<RangeHighlighter> createBorderLineMarker(@NotNull final Editor editor, int line,
                                                              @NotNull final SeparatorPlacement placement) {
    return new LineMarkerBuilder(editor, line, placement).withRenderer(BORDER_LINE_RENDERER).done();
  }

  @NotNull
  public static List<RangeHighlighter> createLineSeparatorHighlighter(@NotNull Editor editor,
                                                                      int offset1,
                                                                      int offset2,
                                                                      @NotNull BooleanGetter condition) {
    return createLineSeparatorHighlighter(editor, offset1, offset2, condition, null);
  }

  @NotNull
  public static List<RangeHighlighter> createLineSeparatorHighlighter(@NotNull Editor editor,
                                                                      int offset1,
                                                                      int offset2,
                                                                      @NotNull BooleanGetter condition,
                                                                      @Nullable Computable<String> description) {
    RangeHighlighter marker = editor.getMarkupModel()
      .addRangeHighlighter(null, offset1, offset2, LINE_MARKER_LAYER, HighlighterTargetArea.LINES_IN_RANGE);

    DiffLineSeparatorRenderer renderer = new DiffLineSeparatorRenderer(editor, condition, description);
    marker.setLineSeparatorPlacement(SeparatorPlacement.TOP);
    marker.setLineSeparatorRenderer(renderer);
    marker.setLineMarkerRenderer(renderer);

    return Collections.singletonList(marker);
  }

  public static final class LineHighlighterBuilder {
    @NotNull private final Editor editor;
    @NotNull private final TextDiffType type;
    private final int startLine;
    private final int endLine;

    private boolean ignored = false;
    private boolean resolved = false;
    private boolean excludedInEditor = false;
    private boolean excludedInGutter = false;
    private boolean hideWithoutLineNumbers = false;
    private boolean hideStripeMarkers = false;

    public LineHighlighterBuilder(@NotNull Editor editor, int startLine, int endLine, @NotNull TextDiffType type) {
      this.editor = editor;
      this.type = type;
      this.startLine = startLine;
      this.endLine = endLine;
    }

    @NotNull
    public LineHighlighterBuilder withIgnored(boolean ignored) {
      this.ignored = ignored;
      return this;
    }

    @NotNull
    public LineHighlighterBuilder withResolved(boolean resolved) {
      this.resolved = resolved;
      return this;
    }

    @NotNull
    public LineHighlighterBuilder withExcluded(boolean excluded) {
      this.excludedInEditor = excluded;
      this.excludedInGutter = excluded;
      return this;
    }

    @NotNull
    public LineHighlighterBuilder withExcludedInEditor(boolean excluded) {
      this.excludedInEditor = excluded;
      return this;
    }

    @NotNull
    public LineHighlighterBuilder withExcludedInGutter(boolean excluded) {
      this.excludedInGutter = excluded;
      return this;
    }

    @NotNull
    public LineHighlighterBuilder withHideWithoutLineNumbers(boolean hideWithoutLineNumbers) {
      this.hideWithoutLineNumbers = hideWithoutLineNumbers;
      return this;
    }

    @NotNull
    public LineHighlighterBuilder withHideStripeMarkers(boolean hideStripeMarkers) {
      this.hideStripeMarkers = hideStripeMarkers;
      return this;
    }

    @NotNull
    public List<RangeHighlighter> done() {
      List<RangeHighlighter> highlighters = new ArrayList<>();

      PaintMode editorMode = PaintMode.DEFAULT;
      PaintMode gutterMode = PaintMode.DEFAULT;
      if (ignored) {
        editorMode = PaintMode.IGNORED;
      }
      if (excludedInEditor) {
        editorMode = PaintMode.EXCLUDED_EDITOR;
      }
      if (excludedInGutter) {
        gutterMode = PaintMode.EXCLUDED_GUTTER;
      }
      if (resolved) {
        editorMode = PaintMode.RESOLVED;
        gutterMode = PaintMode.RESOLVED;
      }

      boolean isEmptyRange = startLine == endLine;
      boolean isFirstLine = startLine == 0;
      boolean isLastLine = endLine == getLineCount(editor.getDocument());

      TextRange offsets = DiffUtil.getLinesRange(editor.getDocument(), startLine, endLine);
      int start = offsets.getStartOffset();
      int end = offsets.getEndOffset();

      TextAttributes attributes = isEmptyRange ? null : getTextAttributes(type, editor, editorMode.background);
      TextAttributes stripeAttributes = hideStripeMarkers || resolved || excludedInEditor
                                        ? null : getStripeTextAttributes(type, editor);
      boolean dottedLine = editorMode.border == BorderType.DOTTED;

      RangeHighlighter highlighter = editor.getMarkupModel()
        .addRangeHighlighter(start, end, DEFAULT_LAYER, attributes, HighlighterTargetArea.LINES_IN_RANGE);
      highlighters.add(highlighter);

      highlighter.setLineMarkerRenderer(new DiffLineMarkerRenderer(highlighter, type, editorMode, gutterMode,
                                                                   hideWithoutLineNumbers, isEmptyRange, isFirstLine, isLastLine));

      if (isEmptyRange) {
        if (isFirstLine) {
          highlighters.addAll(new LineMarkerBuilder(editor, 0, SeparatorPlacement.TOP)
                                .withDefaultRenderer(type, true, dottedLine, highlighter)
                                .done());
        }
        else {
          highlighters.addAll(new LineMarkerBuilder(editor, startLine - 1, SeparatorPlacement.BOTTOM)
                                .withDefaultRenderer(type, true, dottedLine, highlighter)
                                .done());
        }
      }
      else if (editorMode.border != BorderType.NONE) {
        highlighters.addAll(new LineMarkerBuilder(editor, startLine, SeparatorPlacement.TOP)
                              .withDefaultRenderer(type, false, dottedLine, highlighter)
                              .done());
        highlighters.addAll(new LineMarkerBuilder(editor, endLine - 1, SeparatorPlacement.BOTTOM)
                              .withDefaultRenderer(type, false, dottedLine, highlighter)
                              .done());
      }

      if (stripeAttributes != null) {
        RangeHighlighter stripeHighlighter = editor.getMarkupModel()
          .addRangeHighlighter(start, end, STRIPE_LAYER, stripeAttributes, HighlighterTargetArea.LINES_IN_RANGE);
        highlighters.add(stripeHighlighter);
      }

      return highlighters;
    }
  }

  private static class InlineHighlighterBuilder {
    @NotNull private final Editor editor;
    @NotNull private final TextDiffType type;
    private final int start;
    private final int end;

    private InlineHighlighterBuilder(@NotNull Editor editor, int start, int end, @NotNull TextDiffType type) {
      this.editor = editor;
      this.type = type;
      this.start = start;
      this.end = end;
    }

    @NotNull
    public List<RangeHighlighter> done() {
      TextAttributes attributes = getTextAttributes(type, editor, BackgroundType.DEFAULT);

      RangeHighlighter highlighter = editor.getMarkupModel()
        .addRangeHighlighter(start, end, INLINE_LAYER, attributes, HighlighterTargetArea.EXACT_RANGE);

      if (start == end) installEmptyRangeRenderer(highlighter, type);

      return Collections.singletonList(highlighter);
    }
  }

  private static class LineMarkerBuilder {
    @NotNull private final Editor editor;
    @NotNull private final SeparatorPlacement placement;

    private final int offset;
    @NotNull private final RangeHighlighter highlighter;

    @Nullable private LineSeparatorRenderer lineRenderer;
    @Nullable private LineMarkerRenderer gutterRenderer;
    @Nullable private TextAttributes stripeAttributes;

    private LineMarkerBuilder(@NotNull Editor editor, int line, @NotNull SeparatorPlacement placement) {
      this.editor = editor;
      this.placement = placement;

      // We won't use addLineHighlighter as it will fail to add marker into empty document.
      // RangeHighlighter highlighter = editor.getMarkupModel().addLineHighlighter(line, HighlighterLayer.SELECTION - 1, null);

      offset = DocumentUtil.getFirstNonSpaceCharOffset(editor.getDocument(), line);
      highlighter = editor.getMarkupModel()
        .addRangeHighlighter(null, offset, offset, LINE_MARKER_LAYER, HighlighterTargetArea.LINES_IN_RANGE);
    }

    @NotNull
    public LineMarkerBuilder withRenderer(@Nullable LineSeparatorRenderer lineRenderer) {
      this.lineRenderer = lineRenderer;
      return this;
    }

    @NotNull
    public LineMarkerBuilder withGutterRenderer(@Nullable LineMarkerRenderer gutterRenderer) {
      this.gutterRenderer = gutterRenderer;
      return this;
    }

    @NotNull
    public LineMarkerBuilder withStripeAttributes(@Nullable TextAttributes stripeAttributes) {
      this.stripeAttributes = stripeAttributes;
      return this;
    }


    @NotNull
    public LineMarkerBuilder withDefaultRenderer(@NotNull TextDiffType type, boolean doubleLine, boolean dottedLine,
                                                 @Nullable RangeHighlighter parentHighlighter) {
      RangeHighlighter parent = ObjectUtils.chooseNotNull(parentHighlighter, highlighter);
      LineSeparatorRenderer renderer = createDiffLineRenderer(editor, parent, type, placement, doubleLine, dottedLine);
      return withRenderer(renderer);
    }

    @NotNull
    public LineMarkerBuilder withDefaultGutterRenderer(@NotNull TextDiffType type, boolean doubleLine, boolean dottedLine) {
      LineMarkerRenderer renderer = createFoldingGutterLineRenderer(type, placement, doubleLine, dottedLine);
      return withGutterRenderer(renderer);
    }

    @NotNull
    public LineMarkerBuilder withDefaultStripeAttributes(@NotNull TextDiffType type) {
      TextAttributes attributes = getStripeTextAttributes(type, editor);
      return withStripeAttributes(attributes);
    }

    @NotNull
    public List<RangeHighlighter> done() {
      highlighter.setLineSeparatorPlacement(placement);
      highlighter.setLineSeparatorRenderer(lineRenderer);
      highlighter.setLineMarkerRenderer(gutterRenderer);

      if (stripeAttributes == null) return Collections.singletonList(highlighter);

      RangeHighlighter stripeHighlighter = editor.getMarkupModel()
        .addRangeHighlighter(offset, offset, STRIPE_LAYER, stripeAttributes, HighlighterTargetArea.LINES_IN_RANGE);

      return Arrays.asList(highlighter, stripeHighlighter);
    }
  }

  static class PaintMode {
    @NotNull public final BackgroundType background;
    @NotNull public final BorderType border;

    PaintMode(@NotNull BackgroundType background, @NotNull BorderType border) {
      this.background = background;
      this.border = border;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      PaintMode mode = (PaintMode)o;
      return background == mode.background &&
             border == mode.border;
    }

    @Override
    public int hashCode() {
      return Objects.hash(background, border);
    }

    public static final PaintMode DEFAULT = new PaintMode(BackgroundType.DEFAULT, BorderType.NONE);
    public static final PaintMode IGNORED = new PaintMode(BackgroundType.IGNORED, BorderType.NONE);
    public static final PaintMode RESOLVED = new PaintMode(BackgroundType.NONE, BorderType.DOTTED);

    public static final PaintMode EXCLUDED_EDITOR = new PaintMode(BackgroundType.NONE, BorderType.LINE);
    public static final PaintMode EXCLUDED_GUTTER = new PaintMode(BackgroundType.IGNORED, BorderType.LINE);
  }

  enum BackgroundType {
    NONE, DEFAULT, IGNORED
  }

  enum BorderType {
    NONE, LINE, DOTTED
  }

  public static class MarkerRange {
    public final int y1;
    public final int y2;

    public MarkerRange(int y1, int y2) {
      this.y1 = y1;
      this.y2 = y2;
    }

    public int component1() {
      return y1;
    }

    public int component2() {
      return y2;
    }
  }
}
