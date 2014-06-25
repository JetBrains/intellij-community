package ru.compscicenter.edide;


import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Log;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.impl.ProjectLifecycleListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.util.messages.MessageBusConnection;
import org.jdom.DataConversionException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.compscicenter.edide.course.Course;
import ru.compscicenter.edide.course.Task;
import ru.compscicenter.edide.course.TaskFile;

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
public class TaskManager implements ProjectComponent, PersistentStateComponent<Element> {
  private static Map<String, TaskManager> myTaskManagers = new HashMap<String, TaskManager>();
  private final Project myProject;
  private Course myCourse;

  public void setCourse(Course course) {
    myCourse = course;
  }

  public TaskManager(Project project) {
    myTaskManagers.put(project.getBasePath(), this);
    myProject = project;
  }

  public Element saveState(String projectName) {
    Element taskManagerElement = new Element(projectName);
    //for (Task task:tasks) {
    //   taskManagerElement.addContent(task.saveState());
    //}
    return taskManagerElement;
  }


  public Course getCourse() {
    return myCourse;
  }

  @Nullable
  @Override
  public Element getState() {
    return saveState(myProject.getName());
  }

  @Override
  public void loadState(Element state) {

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

  public int getTaskNumForFile(VirtualFile file) {

    return 0;
  }


  private int getIndex(String fullName, String logicalName) {
     return Integer.parseInt(fullName.substring(logicalName.length())) - 1;
  }

  public TaskFile getTaskFile(VirtualFile file) {
   String taskDirName = file.getParent().getName();
   String lessonDirName = file.getParent().getParent().getName();
   Task task = myCourse.getLessons().get(getIndex(lessonDirName, "lesson")).getTaskList().get(getIndex(taskDirName, "task"));
   return task.getFile(file.getName());
  }
}
