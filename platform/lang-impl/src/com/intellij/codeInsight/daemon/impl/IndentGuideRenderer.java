package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.view.EditorPainter;
import com.intellij.openapi.editor.impl.view.VisualLinesIterator;
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.ui.paint.LinePainter2D;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;

public class IndentGuideRenderer implements CustomHighlighterRenderer {
    @Override
    public void paint(@NotNull Editor editor, @NotNull RangeHighlighter highlighter, @NotNull Graphics g) {
        int startOffset = highlighter.getStartOffset();
        final Document doc = highlighter.getDocument();
        if (startOffset >= doc.getTextLength()) return;

        final int endOffset = highlighter.getEndOffset();

        int off;
        int startLine = doc.getLineNumber(startOffset);

        final CharSequence chars = doc.getCharsSequence();
        do {
            int start = doc.getLineStartOffset(startLine);
            int end = doc.getLineEndOffset(startLine);
            off = CharArrayUtil.shiftForward(chars, start, end, " \t");
            startLine--;
        }
        while (startLine > 1 && off < doc.getTextLength() && chars.charAt(off) == '\n');

        final VisualPosition startPosition = editor.offsetToVisualPosition(off);
        int indentColumn = startPosition.column;
        if (indentColumn <= 0) return;

        final FoldingModel foldingModel = editor.getFoldingModel();
        if (foldingModel.isOffsetCollapsed(off)) return;

        final FoldRegion headerRegion = foldingModel.getCollapsedRegionAtOffset(doc.getLineEndOffset(doc.getLineNumber(off)));
        final FoldRegion tailRegion = foldingModel.getCollapsedRegionAtOffset(doc.getLineStartOffset(doc.getLineNumber(endOffset)));

        if (tailRegion != null && tailRegion == headerRegion) return;

        final boolean selected = isSelected(editor, endOffset, off, indentColumn);

        int lineHeight = editor.getLineHeight();
        Point start = editor.visualPositionToXY(startPosition);
        start.y += lineHeight;
        final VisualPosition endPosition = editor.offsetToVisualPosition(endOffset);
        Point end = editor.visualPositionToXY(endPosition);
        int maxY = end.y;
        if (endPosition.line == editor.offsetToVisualPosition(doc.getTextLength()).line) {
            maxY += lineHeight;
        }

        Rectangle clip = g.getClipBounds();
        if (clip != null) {
            if (clip.y >= maxY || clip.y + clip.height <= start.y) {
                return;
            }
            maxY = Math.min(maxY, clip.y + clip.height);
        }

        if (start.y >= maxY) return;

        int targetX = Math.max(0, start.x + EditorPainter.getIndentGuideShift(editor));
        final EditorColorsScheme scheme = editor.getColorsScheme();
        g.setColor(scheme.getColor(selected ? EditorColors.SELECTED_INDENT_GUIDE_COLOR : EditorColors.INDENT_GUIDE_COLOR));
        // There is a possible case that indent line intersects soft wrap-introduced text. Example:
        //     this is a long line <soft-wrap>
        // that| is soft-wrapped
        //     |
        //     | <- vertical indent
        //
        // Also it's possible that no additional intersections are added because of soft wrap:
        //     this is a long line <soft-wrap>
        //     |   that is soft-wrapped
        //     |
        //     | <- vertical indent
        // We want to use the following approach then:
        //     1. Show only active indent if it crosses soft wrap-introduced text;
        //     2. Show indent as is if it doesn't intersect with soft wrap-introduced text;
        List<? extends SoftWrap> softWraps = ((EditorEx)editor).getSoftWrapModel().getRegisteredSoftWraps();
        if (selected || softWraps.isEmpty()) {
            LinePainter2D.paint((Graphics2D)g, targetX, start.y, targetX, maxY - 1);
        }
        else {
            int startY = start.y;
            int startVisualLine = startPosition.line + 1;
            if (clip != null && startY < clip.y) {
                startY = clip.y;
                startVisualLine = editor.yToVisualLine(clip.y);
            }
            VisualLinesIterator it = new VisualLinesIterator((EditorImpl)editor, startVisualLine);
            while (!it.atEnd()) {
                int currY = it.getY();
                if (currY >= startY) {
                    if (currY >= maxY) break;
                    if (it.startsWithSoftWrap()) {
                        SoftWrap softWrap = softWraps.get(it.getStartOrPrevWrapIndex());
                        if (softWrap.getIndentInColumns() < indentColumn) {
                            if (startY < currY) {
                                LinePainter2D.paint((Graphics2D)g, targetX, startY, targetX, currY - 1);
                            }
                            startY = currY + lineHeight;
                        }
                    }
                }
                it.advance();
            }
            if (startY < maxY) {
                LinePainter2D.paint((Graphics2D)g, targetX, startY, targetX, maxY - 1);
            }
        }

    }

    protected boolean isSelected(@NotNull Editor editor, int endOffset, int off, int indentColumn) {
        final IndentGuideDescriptor guide = editor.getIndentsModel().getCaretIndentGuide();
        if (guide == null) return false;
        return isCaretOnGuide(editor, endOffset, off, indentColumn);
    }

    protected final boolean isCaretOnGuide(@NotNull Editor editor, int endOffset, int off, int indentColumn) {
        final CaretModel caretModel = editor.getCaretModel();
        final int caretOffset = caretModel.getOffset();
        return caretOffset >= off && caretOffset < endOffset && caretModel.getLogicalPosition().column == indentColumn;
    }
}
