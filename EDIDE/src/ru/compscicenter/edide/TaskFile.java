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

public class TaskFile {
  private final String name;
  private final ArrayList<TaskWindow> taskWindows;
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
                                tw.draw(editor);
                                return;
                            }
                        }
                    }
                });
            }
        });
    }

    public void resolveCurrentHighlighter(final Editor editor, final LogicalPosition pos) {
//        ApplicationManager.getApplication().invokeLater(new Runnable() {
//            @Override
//            public void run() {
//                ApplicationManager.getApplication().runWriteAction(new Runnable() {
//                    @Override
//                    public void run() {
//                        int currentPosition = editor.getDocument().getLineStartOffset(pos.line) + pos.column;
//                        Iterator<TaskWindowHighlighter> it = myHighlighters.iterator();
//                        while(it.hasNext()) {
//                            TaskWindowHighlighter rh = it.next();
//                            if (rh.getRangeHighlighter().getStartOffset() < currentPosition
//                                    && currentPosition <rh.getRangeHighlighter().getEndOffset()) {
//                                editor.getMarkupModel().
//                                rh.getRangeHighlighter().dispose();
//                                //TODO: get new offsets
//                                rh.getTaskWindow().setResolved();
//                                return;
//                            }
//                        }
//                    }
//                });
//            }
//        });
    }
}
