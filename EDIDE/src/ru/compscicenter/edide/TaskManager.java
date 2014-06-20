package ru.compscicenter.edide;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
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
  static private TaskManager ourInstance = null;
  private final ArrayList<Task> myTasks;
  private int myCurrentTask = -1;

  private TaskManager() {
    myTasks = new ArrayList<Task>(10);
  }

  static public TaskManager getInstance() {
    if (ourInstance == null) {
      ourInstance = new TaskManager();
      ourInstance.load();
    }
    return ourInstance;
  }

  private void load() {
    InputStream metaIS = StudyDirectoryProjectGenerator.class.getResourceAsStream("tasks.json");
    BufferedReader reader = new BufferedReader(new InputStreamReader(metaIS));
    JsonReader r = new JsonReader(reader);
    JsonParser parser = new JsonParser();
    try {
      JsonElement el = parser.parse(r);
      JsonArray tasksList = el.getAsJsonObject().get("tasks_list").getAsJsonArray();
      for (JsonElement e : tasksList) {
        Task task = new Task(e.getAsJsonObject().get("file_num").getAsInt());
        JsonArray filesInTask = e.getAsJsonObject().get("file_names").getAsJsonArray();
        for (JsonElement fileName : filesInTask) {
            task.addNewTaskFile(fileName.getAsString());
        }
        String testFileName = e.getAsJsonObject().get("test").getAsString();
        task.setTest(testFileName);
        String taskTextFileName = e.getAsJsonObject().get("task text").getAsString();
        task.setTaskTextFile(taskTextFileName);
        myTasks.add(task);
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
    return myTasks.size();
  }

  public String getTest(int index) {
    return myTasks.get(index).getTest();
  }

  public int getTaskFileNum(int index) {
    return myTasks.get(index).getFileNum();
  }

  public String getFileName(int taskIndex, int fileIndex) {
    return myTasks.get(taskIndex).getTaskFile(fileIndex).getMyName();
  }

  public String getTaskText(int index) {
    return myTasks.get(index).getTaskText();
  }

  public int getCurrentTask() {
    return myCurrentTask;
  }

  public void setCurrentTask(int currentTask) {
    this.myCurrentTask = currentTask;
  }

  public void incrementTask() {
    myCurrentTask++;
  }

  public int getTaskNumForFile(String filename) {
    for (int i = 0; i < myTasks.size(); i++) {
      if (myTasks.get(i).contains(filename)) {
        return i;
      }
    }
    return -1;
  }

  public TaskFile getTaskFile(int index, String fileName) {
    return myTasks.get(index).getTaskFileByName(fileName);
  }

  public String getDocFileForTask(int taskNum, LogicalPosition pos, String name) {
    Task task = myTasks.get(taskNum);
    TaskFile file = task.getTaskFileByName(name);
    TaskWindow tw = file.getTaskWindow(pos);
    if (tw != null) {
      return tw.getDocsFile();
    }
    return null;
  }
}
