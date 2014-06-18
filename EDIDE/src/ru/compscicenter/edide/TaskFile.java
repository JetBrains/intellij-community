package ru.compscicenter.edide;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileDocumentManager;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * User: lia
 * Date: 30.05.14
 * Time: 20:30
 */
class TaskWindowHighlighter {
    private TaskWindow myTaskWindow;
    private RangeHighlighter myRangeHighlighter;

    TaskWindowHighlighter(TaskWindow taskWindow, RangeHighlighter rangeHighlighter) {
        myTaskWindow = taskWindow;
        myRangeHighlighter = rangeHighlighter;
    }

    public TaskWindow getTaskWindow() {
        return myTaskWindow;
    }

    public RangeHighlighter getRangeHighlighter() {
        return myRangeHighlighter;
    }
}

public class TaskFile {
  private final String name;
  private final ArrayList<TaskWindow> taskWindows;
  private ArrayList<TaskWindowHighlighter> myHighlighters;
  public TaskFile(String name, int taskWindowsNum) {
    this.name = name;
    taskWindows = new ArrayList<TaskWindow>(taskWindowsNum);
    // num of highlighters = num of task windows
    myHighlighters = new ArrayList<TaskWindowHighlighter>(taskWindowsNum);
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

    public void drawFirstUnresolved(final Editor editor) {
        //TODO: maybe it's worth to find window with min startOffset
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
                ApplicationManager.getApplication().runWriteAction(new Runnable() {
                    @Override
                    public void run() {
                        for (TaskWindow tw:taskWindows) {
                            if (!tw.getResolveStatus()) {
                                RangeHighlighter rh = tw.draw(editor);
                                myHighlighters.add(new TaskWindowHighlighter(tw, rh));
                                return;
                            }
                        }
                    }
                });
            }
        });
    }

    public void resolveCurrentHighlighter(final Editor editor, final LogicalPosition pos) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
                ApplicationManager.getApplication().runWriteAction(new Runnable() {
                    @Override
                    public void run() {
                        int currentPosition = editor.getDocument().getLineStartOffset(pos.line) + pos.column;
                        Iterator<TaskWindowHighlighter> it = myHighlighters.iterator();
                        while(it.hasNext()) {
                            TaskWindowHighlighter rh = it.next();
                            if (rh.getRangeHighlighter().getStartOffset() < currentPosition
                                    && currentPosition <rh.getRangeHighlighter().getEndOffset()) {
                                rh.getRangeHighlighter().dispose();
                                rh.getTaskWindow().setResolved();
                                return;
                            }
                        }
                    }
                });
            }
        });
    }
}
