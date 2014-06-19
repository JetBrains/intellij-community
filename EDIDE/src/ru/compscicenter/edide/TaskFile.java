package ru.compscicenter.edide;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.ArrayList;

/**
 * User: lia
 * Date: 30.05.14
 * Time: 20:30
 * Implementation of task file. Task file contains task windows.
 */

public class TaskFile {
    private final String myName;
    private final ArrayList<TaskWindow> myTaskWindows;
    private int myLastLineNum;

    public TaskFile(String name, int taskWindowsNum) {
        myName = name;
        myTaskWindows = new ArrayList<TaskWindow>(taskWindowsNum);
    }

    public void addTaskWindow(TaskWindow taskWindow) {
        myTaskWindows.add(taskWindow);
    }

    public String getMyName() {
        return myName;
    }


    public TaskWindow getTaskWindow(LogicalPosition pos) {
        int line = pos.line + 1;
        int offset = pos.column;
        int i = 0;
        while (i < myTaskWindows.size() && (myTaskWindows.get(i).getLine() < line ||
                (myTaskWindows.get(i).getLine() == line && myTaskWindows.get(i).getStartOffset() < offset))) {
            i++;
        }
        if (i == 0) {
            return null;
        }
        return myTaskWindows.get(i - 1);
    }

    public int getTaskWindowNum() {
        return myTaskWindows.size();
    }

    public void resolveCurrentHighlighter(final Editor editor) {
        RangeHighlighter[] rm = editor.getMarkupModel().getAllHighlighters();
        int highlighterStartOffset = -1;
        int highlighterEndOffset = -1;
        for (RangeHighlighter rh:rm) {
            if (rh.getLayer() == (HighlighterLayer.LAST + 1)) {
                highlighterStartOffset = rh.getStartOffset();
                highlighterEndOffset = rh.getEndOffset();
                rh.dispose();
                break;
            }
        }
        if (highlighterStartOffset == -1) {
            return;
        }
        VirtualFile vfOpenedFile = FileDocumentManager.getInstance().getFile(editor.getDocument());
        boolean toBeDrawn = false;
        for (TaskWindow tw : myTaskWindows) {
            if (toBeDrawn) {
                if (tw.getResolveStatus()) {
                    return;
                }
                tw.draw(editor, true);
                return;
            }
            int startOffset = tw.getRangeHighlighterStartOffset();
            int endOffset = tw.getRangeHighlighterEndOffset();
            if (startOffset != -1 && endOffset != -1) {
                if (startOffset == highlighterStartOffset) {
                    tw.setResolved();
                    int newLineNum = editor.getDocument().getLineCount();
                    int highlighterStartLine = getLineNumByOffset(editor, highlighterStartOffset);
                    int highlighterEndLine = getLineNumByOffset(editor, highlighterEndOffset);
                    if (newLineNum != myLastLineNum) {
                        int deltaLines = newLineNum - myLastLineNum;
                        myLastLineNum = newLineNum;
                        incrementAllLines(highlighterStartLine - deltaLines, deltaLines);
                        tw.incrementLine(-deltaLines);
                        incrementAllInLine(editor, highlighterEndLine, endOffset);
                    } else {
                        int delta = highlighterEndOffset - endOffset;
                        incrementAllInLineAfterOffset(highlighterEndLine, tw.getStartOffset(), delta);
                    }
                    tw.setOffsetInLine(highlighterEndOffset - highlighterStartOffset);
                    toBeDrawn = true;
                    FileDocumentManager.getInstance().saveAllDocuments();
                    FileDocumentManager.getInstance().reloadFiles(vfOpenedFile);
                }
            }
        }
    }

    private void incrementAllInLineAfterOffset(int highlighterEndLine, int offset, int delta) {
        for (TaskWindow tw : myTaskWindows) {
            if (tw.getLine() == highlighterEndLine) {
                if (tw.getStartOffset() > offset) {
                    tw.incrementStartOffset(delta);
                }
            }
        }
    }

    private void incrementAllInLine(Editor editor, int highlighterLine, int endOffset) {
        int delta = endOffset - editor.getDocument().getLineStartOffset(highlighterLine);
        for (TaskWindow tw : myTaskWindows) {
            if (tw.getLine() == highlighterLine) {
                tw.incrementStartOffset(delta);
            }
        }
    }

    private int getLineNumByOffset(Editor editor, int offset) {
        int i = 0;
        int lineStartOffset = editor.getDocument().getLineStartOffset(i);
        while(i < editor.getDocument().getLineCount() && lineStartOffset < offset) {
            i++;
            if (i < editor.getDocument().getLineCount()) {
                lineStartOffset = editor.getDocument().getLineStartOffset(i);
            }
        }
        return i - 1;
    }

    private void incrementAllLines(int highlighterLine, int delta) {
        for (TaskWindow tw : myTaskWindows) {
            if (tw.getLine() >= highlighterLine) {
                tw.incrementLine(delta);
            }
        }
    }

    public void drawAllWindows(Editor editor) {
        for (TaskWindow tw: myTaskWindows){
            tw.draw(editor, false);
        }
    }

    TaskWindow getTaskWindowByPos(Editor editor, LogicalPosition pos) {
        int line = pos.line;
        int column = pos.column;
        int realOffset = editor.getDocument().getLineStartOffset(line) + column;
        for (TaskWindow tw: myTaskWindows) {
            if (line == tw.getLine()) {
                int twStartOffset = tw.getRealStartOffset(editor);
                int twEndOffset = twStartOffset + tw.getOffsetInLine();
                if (twStartOffset < realOffset && realOffset < twEndOffset) {
                    return tw;
                }
            }
        }
        return null;
    }

    public void drawWindowByPos(Editor editor, LogicalPosition pos) {
        TaskWindow tw = getTaskWindowByPos(editor, pos);
        if (tw == null) {
            return;
        }
        if (tw.getResolveStatus()) {
            tw.draw(editor, false);
        } else {
            tw.draw(editor, true);
        }
        myLastLineNum = editor.getDocument().getLineCount();
    }
}
