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
 */

public class TaskFile {
    private final String name;
    private final ArrayList<TaskWindow> taskWindows;
    private int myLastLength;
    private int myLastLineNum;

    public TaskFile(String name, int taskWindowsNum) {
        this.name = name;
        taskWindows = new ArrayList<TaskWindow>(taskWindowsNum);
    }

    public void addTaskWindow(TaskWindow taskWindow) {
        taskWindows.add(taskWindow);
    }

    public String getName() {
        return name;
    }


    public TaskWindow getTaskWindow(LogicalPosition pos) {
        int line = pos.line + 1;
        int offset = pos.column;
        int i = 0;
        while (i < taskWindows.size() && (taskWindows.get(i).getLine() < line ||
                (taskWindows.get(i).getLine() == line && taskWindows.get(i).getStartOffset() < offset))) {
            i++;
        }
        if (i == 0) {
            return null;
        }
        return taskWindows.get(i - 1);
    }

    public int getTaskWindowNum() {
        return taskWindows.size();
    }

    public TaskWindow getTaskWindowByIndex(int index) {
        return taskWindows.get(index);
    }

    public void drawFirstUnresolved(final Editor editor, boolean drawSelection) {
        myLastLength = editor.getDocument().getTextLength();
        myLastLineNum = editor.getDocument().getLineCount();
        //TODO: maybe it's worth to find window with min startOffset
        for (TaskWindow tw : taskWindows) {
            if (!tw.getResolveStatus()) {
                tw.draw(editor, drawSelection);
                return;
            }
        }
    }

    public void resolveCurrentHighlighter(final Editor editor, final LogicalPosition pos) {
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
        for (TaskWindow tw : taskWindows) {
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
                //TODO:писать можно не только вправо
                if (startOffset == highlighterStartOffset) {
                    tw.setResolved();
                    int newLineNum = editor.getDocument().getLineCount();
                    int highlighterStartLine = getLineNumByOffset(editor, highlighterStartOffset);
                    int highlighterEndLine = getLineNumByOffset(editor, highlighterEndOffset);
                    if (newLineNum != myLastLineNum) {
                        int deltaLines = newLineNum - myLastLineNum;
                        myLastLineNum = newLineNum;
                        incrementAllLines(editor, highlighterStartLine - deltaLines, deltaLines);
                        tw.incrementLine(-deltaLines);
                        incrementAllInLine(editor, highlighterEndLine, endOffset);
                    } else {
                        int delta = highlighterEndOffset - endOffset;
                        incrementAllInLineAfterOffset(editor, highlighterEndLine, tw.getStartOffset(), delta);
                    }
                    //TODO:update tw end offset
                    tw.setOffsetInLine(highlighterEndOffset - highlighterStartOffset);
                    toBeDrawn = true;
                    FileDocumentManager.getInstance().saveAllDocuments();
                    FileDocumentManager.getInstance().reloadFiles(vfOpenedFile);
                }
            }
        }
    }

    private void incrementAllInLineAfterOffset(Editor editor, int highlighterEndLine, int offset, int delta) {
        for (TaskWindow tw : taskWindows) {
            if (tw.getLine() == highlighterEndLine) {
                if (tw.getStartOffset() > offset) {
                    tw.incrementStartOffset(delta);
                }
            }
        }
    }

    private void incrementAllInLine(Editor editor, int highlighterLine, int endOffset) {
        int delta = endOffset - editor.getDocument().getLineStartOffset(highlighterLine);
        for (TaskWindow tw : taskWindows) {
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

    private void incrementAllLines(Editor editor, int highlighterLine, int delta) {
        for (TaskWindow tw : taskWindows) {
            if (tw.getLine() >= highlighterLine) {
                tw.incrementLine(delta);
            }
        }
    }

    public void drawAllWindows(Editor editor) {
        for (TaskWindow tw:taskWindows){
            tw.draw(editor, false);
        }
    }

    public TaskWindow getTaskWindowByPos(Editor editor, LogicalPosition pos) {
        int line = pos.line;
        int column = pos.column;
        int realOffset = editor.getDocument().getLineStartOffset(line) + column;
        for (TaskWindow tw:taskWindows) {
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
