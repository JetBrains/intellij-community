package ru.compscicenter.edide;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.ui.JBColor;

/**
 * User: lia
 * Date: 30.05.14
 * Time: 20:34
 * Frame with task implementation
 */
public class TaskWindow {
    private final int myLine;
    private final int myStartOffsetInLine;
    private final String myText;
    private final String myDocsFile;
    private boolean myResolveStatus;
    private RangeHighlighter myRangeHighlighter;

    public TaskWindow(int line, int startOffset, String text,
                      String docsFile) {
        myLine = line - 1;
        myStartOffsetInLine = startOffset;
        myText = text;
        myDocsFile = docsFile;
        myResolveStatus = false;
        myRangeHighlighter = null;
    }

    public RangeHighlighter getRangeHighlighter() {
        return myRangeHighlighter;
    }
    public boolean getResolveStatus() {
        return myResolveStatus;
    }

    public void setResolved() {
        myResolveStatus = true;
    }

    public int getLine() {
        return myLine;
    }

    public int getStartOffset() {
        return myStartOffsetInLine;
    }

    public String getText() {
        return myText;
    }

    public String getDocsFile() {
        return myDocsFile;
    }

    public void draw(final Editor e) {
        JBColor color = JBColor.YELLOW;
        if (myResolveStatus) {
            color = JBColor.GREEN;
        }
        final TextAttributes ta = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.LIVE_TEMPLATE_ATTRIBUTES);
        ta.setEffectColor(color);
        final int startOffset = e.getDocument().getLineStartOffset(myLine) + myStartOffsetInLine;
        e.getCaretModel().moveToOffset(startOffset);
        final RangeHighlighter rh;
        myRangeHighlighter = e.getMarkupModel().addRangeHighlighter(startOffset, startOffset + myText.length(), HighlighterLayer.LAST + 1, ta, HighlighterTargetArea.EXACT_RANGE);
        myRangeHighlighter.setGreedyToLeft(true);
        myRangeHighlighter.setGreedyToRight(true);
    }
}