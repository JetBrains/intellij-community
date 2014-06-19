package ru.compscicenter.edide;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.*;

/**
 * User: lia
 * Date: 30.05.14
 * Time: 20:34
 * Frame with task implementation
 */
public class TaskWindow {
    private int myLine;
    private int myStartOffsetInLine;
    private int myOffsetInLine;
    private final String myDocsFile;
    private boolean myResolveStatus;
    private int myRangeHighlighterStartOffset;
    private int myRangeHighlighterEndOffset;

    public int getOffsetInLine() {
        return myOffsetInLine;
    }

    public TaskWindow(int line, int startOffset, String text,
                      String docsFile) {
        myLine = line - 1;
        myStartOffsetInLine = startOffset;
        myOffsetInLine = text.length();
        myDocsFile = docsFile;
        myResolveStatus = false;
        myRangeHighlighterStartOffset = -1;
        myRangeHighlighterEndOffset = -1;
    }
    public int getRealStartOffset(Editor editor) {
        return editor.getDocument().getLineStartOffset(myLine) + myStartOffsetInLine;
    }
    public int getRangeHighlighterStartOffset() {
        return myRangeHighlighterStartOffset;
    }

    public int getRangeHighlighterEndOffset() {
        return myRangeHighlighterEndOffset;
    }

   void setOffsets(int startOffset, int endOffset) {
        myRangeHighlighterStartOffset = startOffset;
        myRangeHighlighterEndOffset = endOffset;
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

    public String getDocsFile() {
        return myDocsFile;
    }

    public void draw(final Editor e, boolean drawSelection) {
        final TextAttributes ta = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.LIVE_TEMPLATE_ATTRIBUTES);
        //ta.setEffectColor(color);
        final int startOffset = e.getDocument().getLineStartOffset(myLine) + myStartOffsetInLine;

        RangeHighlighter rh = e.getMarkupModel().addRangeHighlighter(startOffset, startOffset + myOffsetInLine, HighlighterLayer.LAST + 1, ta, HighlighterTargetArea.EXACT_RANGE);

        setOffsets(rh.getStartOffset(), rh.getEndOffset());
        if (drawSelection) {
            e.getSelectionModel().setSelection(startOffset, startOffset + myOffsetInLine);
            e.getCaretModel().moveToOffset(startOffset);
        }

        rh.setGreedyToLeft(true);
        rh.setGreedyToRight(true);
    }

    public void setOffsetInLine(int offsetInLine) {
        myOffsetInLine = offsetInLine;
    }

    public void incrementStartOffset(int delta) {
        myStartOffsetInLine += delta;
    }
    public  void incrementLine(int delta) {
        myLine += delta;
    }
}