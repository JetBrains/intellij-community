package ru.compscicenter.edide;

import com.intellij.openapi.editor.LogicalPosition;

import java.util.ArrayList;

/**
 * User: lia
 * Date: 30.05.14
 * Time: 20:30
 */
public class TaskFile {
  private String name;
  private ArrayList<TaskWindow> taskWindows;

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
}
