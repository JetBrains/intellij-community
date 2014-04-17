package com.intellij.execution.console;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.RangeMarkerImpl;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.util.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

final class LineSeparatorPainter extends RangeMarkerImpl implements RangeHighlighterEx, Getter<RangeHighlighterEx> {
  private final GutterContentProvider gutterContentProvider;

  private final CustomHighlighterRenderer renderer = new CustomHighlighterRenderer() {
    @Override
    public void paint(@NotNull Editor editor, @NotNull RangeHighlighter highlighter, @NotNull Graphics g) {
      Rectangle clip = g.getClipBounds();
      int lineHeight = editor.getLineHeight();
      int startLine = clip.y / lineHeight;
      int endLine = Math.min(((clip.y + clip.height) / lineHeight) + 1, ((EditorImpl)editor).getVisibleLineCount());
      if (startLine >= endLine) {
        return;
      }

      // workaround - editor ask us to paint line 4-6, but we should draw line for line 3 (startLine - 1) also, otherwise it will be not rendered
      int actualStartLine = startLine == 0 ? 0 : startLine - 1;
      int y = (actualStartLine + 1) * lineHeight;
      g.setColor(editor.getColorsScheme().getColor(EditorColors.INDENT_GUIDE_COLOR));
      for (int visualLine = actualStartLine; visualLine < endLine; visualLine++) {
        if (gutterContentProvider.isShowSeparatorLine(editor.visualToLogicalPosition(new VisualPosition(visualLine, 0)).line, editor)) {
          g.drawLine(clip.x, y, clip.x + clip.width, y);
        }
        y += lineHeight;
      }
    }
  };

  private final MarkupModelEx markupModel;

  public LineSeparatorPainter(GutterContentProvider gutterContentProvider, @NotNull EditorEx editor, int endOffset) {
    super(editor.getDocument(), 0, endOffset, false);

    this.gutterContentProvider = gutterContentProvider;

    markupModel = editor.getMarkupModel();
    registerInTree(0, endOffset, false, false, HighlighterLayer.ADDITIONAL_SYNTAX);
  }

  @Override
  protected void changedUpdateImpl(DocumentEvent e) {
    setIntervalEnd(myDocument.getTextLength());
  }

  @Override
  public boolean setValid(boolean value) {
    return super.setValid(value);
  }

  @Override
  public boolean isAfterEndOfLine() {
    return false;
  }

  @Override
  public void setAfterEndOfLine(boolean value) {
  }

  @Override
  public int getAffectedAreaStartOffset() {
    return 0;
  }

  @Override
  public int getAffectedAreaEndOffset() {
    return myDocument.getTextLength();
  }

  @Override
  public void setTextAttributes(@NotNull TextAttributes textAttributes) {
  }

  @NotNull
  @Override
  public HighlighterTargetArea getTargetArea() {
    return HighlighterTargetArea.EXACT_RANGE;
  }

  @Nullable
  @Override
  public TextAttributes getTextAttributes() {
    return null;
  }

  @Nullable
  @Override
  public LineMarkerRenderer getLineMarkerRenderer() {
    return null;
  }

  @Override
  public void setLineMarkerRenderer(@Nullable LineMarkerRenderer renderer) {
  }

  @Nullable
  @Override
  public CustomHighlighterRenderer getCustomRenderer() {
    return renderer;
  }

  @Override
  public void setCustomRenderer(CustomHighlighterRenderer renderer) {
  }

  @Nullable
  @Override
  public GutterIconRenderer getGutterIconRenderer() {
    return null;
  }

  @Override
  public void setGutterIconRenderer(@Nullable GutterIconRenderer renderer) {
  }

  @Nullable
  @Override
  public Color getErrorStripeMarkColor() {
    return null;
  }

  @Override
  public void setErrorStripeMarkColor(@Nullable Color color) {
  }

  @Nullable
  @Override
  public Object getErrorStripeTooltip() {
    return null;
  }

  @Override
  public void setErrorStripeTooltip(@Nullable Object tooltipObject) {
  }

  @Override
  public boolean isThinErrorStripeMark() {
    return false;
  }

  @Override
  public void setThinErrorStripeMark(boolean value) {
  }

  @Nullable
  @Override
  public Color getLineSeparatorColor() {
    return null;
  }

  @Override
  public void setLineSeparatorColor(@Nullable Color color) {
  }

  @Override
  public void setLineSeparatorRenderer(LineSeparatorRenderer renderer) {
  }

  @Override
  public LineSeparatorRenderer getLineSeparatorRenderer() {
    return null;
  }

  @Nullable
  @Override
  public SeparatorPlacement getLineSeparatorPlacement() {
    return null;
  }

  @Override
  public void setLineSeparatorPlacement(@Nullable SeparatorPlacement placement) {
  }

  @Override
  public void setEditorFilter(@NotNull MarkupEditorFilter filter) {
  }

  @NotNull
  @Override
  public MarkupEditorFilter getEditorFilter() {
    return MarkupEditorFilter.EMPTY;
  }

  @Override
  public RangeHighlighterEx get() {
    return this;
  }

  @Override
  protected void registerInTree(int start, int end, boolean greedyToLeft, boolean greedyToRight, int layer) {
    markupModel.addRangeHighlighter(this, start, end, greedyToLeft, greedyToRight, layer);
  }

  @Override
  protected boolean unregisterInTree() {
    if (!isValid()) {
      return false;
    }

    // we store highlighters in MarkupModel
    markupModel.removeHighlighter(this);
    return true;
  }
}