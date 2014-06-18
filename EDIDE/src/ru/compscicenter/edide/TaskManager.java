package ru.compscicenter.edide;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.intellij.openapi.diagnostic.Log;
import com.intellij.openapi.editor.LogicalPosition;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

/**
 * User: lia
 * Date: 26.12.13
 * Time: 20:37
 */
public class TaskManager {
  static private TaskManager instance = null;
  private final ArrayList<Task> tasks;
  private int currentTask = -1;

  private TaskManager() {
    tasks = new ArrayList<Task>(10);
  }

  static public TaskManager getInstance() {
    if (instance == null) {
      instance = new TaskManager();
      instance.load();
    }
    return instance;
  }

  private void load() {
    InputStream metaIS = StudyDirectoryProjectGenerator.class.getResourceAsStream("tasks.json");
    BufferedReader reader = new BufferedReader(new InputStreamReader(metaIS));
    com.google.gson.stream.JsonReader r = new com.google.gson.stream.JsonReader(reader);
    JsonParser parser = new JsonParser();
    try {
      com.google.gson.JsonElement el = parser.parse(r);
      JsonArray tasks_list = el.getAsJsonObject().get("tasks_list").getAsJsonArray();
      for (com.google.gson.JsonElement e : tasks_list) {
        Task task = new Task(e.getAsJsonObject().get("file_num").getAsInt());
        JsonArray files_in_task = e.getAsJsonObject().get("file_names").getAsJsonArray();
        for (com.google.gson.JsonElement fileName : files_in_task) {
            task.addNewTaskFile(fileName.getAsString());
        }
        String testFileName = e.getAsJsonObject().get("test").getAsString();
        task.setTest(testFileName);
        String taskTextFileName = e.getAsJsonObject().get("task text").getAsString();
        task.setTaskTextFile(taskTextFileName);
        tasks.add(task);
      }
    }
    catch (Exception e) {
      Log.print("Something wrong with meta file: " + e.getCause());
      Log.flush();
    }
    finally {
      try {
        reader.close();
      }
      catch (IOException e) {
        // close silently
      }
    }
  }


  public int getTasksNum() {
    return tasks.size();
  }

  public String getTest(int index) {
    return tasks.get(index).getTest();
  }

  public int getTaskFileNum(int index) {
    return tasks.get(index).getFileNum();
  }

  public String getFileName(int taskIndex, int fileIndex) {
    return tasks.get(taskIndex).getTaskFile(fileIndex).getName();
  }

  public String getTaskText(int index) {
    return tasks.get(index).getTaskText();
  }

  public int getCurrentTask() {
    return currentTask;
  }

  public void setCurrentTask(int currentTask) {
    this.currentTask = currentTask;
  }

  public void incrementTask() {
    currentTask++;
  }

  public int getTaskNumForFile(String filename) {
    for (int i = 0; i < tasks.size(); i++) {
      if (tasks.get(i).contains(filename)) {
        return i;
      }
    }
    return -1;
  }

  public TaskFile getTaskFile(int index, String fileName) {
    return tasks.get(index).getTaskFileByName(fileName);
  }

  public String getDocFileForTask(int taskNum, LogicalPosition pos, String name) {
    Task task = tasks.get(taskNum);
    TaskFile file = task.getTaskFileByName(name);
    TaskWindow tw = file.getTaskWindow(pos);
    if (tw != null) {
      return tw.getDocsFile();
    }
    return null;
  }
}
