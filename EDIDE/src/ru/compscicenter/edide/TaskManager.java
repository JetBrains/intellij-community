package ru.compscicenter.edide;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Log;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.impl.ProjectLifecycleListener;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.util.messages.MessageBusConnection;
import org.jdom.DataConversionException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: lia
 * Date: 26.12.13
 * Time: 20:37
 */


@State(
  name = "StudySettings",
  storages = {
    @Storage(
      id = "main",
      file = "$PROJECT_CONFIG_DIR$/study.xml"
    )}
)
public class TaskManager implements ProjectComponent, PersistentStateComponent<Element>{
    private static Map<String, TaskManager> myTaskManagers = new HashMap<String, TaskManager>();
    private final ArrayList<Task> tasks;
  private final Project myProject;

  public TaskManager(Project project) {
        myTaskManagers.put(project.getBasePath(), this);
        myProject = project;
        Log.print("Creating new TaskManager\n");
        Log.flush();
        tasks = new ArrayList<Task>(10);
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
        } catch (Exception e) {
            Log.print("Something wrong with meta file: " + e.getCause());
            Log.flush();
        } finally {
            try {
                reader.close();
            } catch (IOException e) {
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
        return tasks.get(taskIndex).getTaskFile(fileIndex).getMyName();
    }

    public String getTaskText(int index) {
        return tasks.get(index).getTaskText();
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

    public Element saveState(String projectName) {
        Element taskManagerElement = new Element(projectName);
        for (Task task:tasks) {
           taskManagerElement.addContent(task.saveState());
        }
        return taskManagerElement;
    }

  @Nullable
  @Override
  public Element getState() {
    return saveState(myProject.getName());
  }

  public void loadState(Element el) {
        List<Element> taskElements = el.getChildren();
        for (Element taskElement:taskElements) {
            List<Element> taskFileElements = taskElement.getChildren();
            int n = taskFileElements.size();
            Task task = new Task(n);
            task.setTest(taskElement.getAttributeValue("testFile"));
            task.setTaskTextFile(taskElement.getAttributeValue("taskText"));
          try {
            task.loadState(taskFileElements);
          }
          catch (DataConversionException e) {
            e.printStackTrace();
          }
          tasks.add(task);

        }
    }

  @Override
  public void projectOpened() {

  }

  @Override
  public void projectClosed() {

  }

  @Override
  public void initComponent() {
    EditorFactory.getInstance().addEditorFactoryListener(new StudyEditorFactoryListener(), myProject);
  }

  @Override
  public void disposeComponent() {

  }

  @NotNull
  @Override
  public String getComponentName() {
    return "taskManager";
  }

  public static TaskManager getInstance(Project project) {
    return myTaskManagers.get(project.getBasePath());
  }
}
