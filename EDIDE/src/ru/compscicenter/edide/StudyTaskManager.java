package ru.compscicenter.edide;



import com.google.gson.annotations.Expose;
import com.intellij.openapi.components.*;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.DataConversionException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.compscicenter.edide.course.*;
import ru.compscicenter.edide.course.Task;
import ru.compscicenter.edide.course.TaskFile;
import ru.compscicenter.edide.course.Window;
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
      id = "others",
      file = "$PROJECT_CONFIG_DIR$/study_project.xml",
      scheme = StorageScheme.DIRECTORY_BASED
    )}
)
public class StudyTaskManager implements ProjectComponent, PersistentStateComponent<Element> {
  private static Map<String, StudyTaskManager> myTaskManagers = new HashMap<String, StudyTaskManager>();
  private final Project myProject;
  private Course myCourse;


  public void setCourse(Course course) {
    myCourse = course;
  }

  private StudyTaskManager(Project project) {
    myTaskManagers.put(project.getBasePath(), this);
    myProject = project;
    myCourse = null;
  }


  public Course getCourse() {
    return myCourse;
  }

  public Element saveState(String projectName) {
    Element taskManagerElement = new Element(projectName);
    if (myCourse == null) {
      return taskManagerElement;
    }
    for (Lesson lesson : myCourse.getLessons()) {
      taskManagerElement.addContent(lesson.saveState());
    }
    return taskManagerElement;
  }



  @Nullable
  @Override
  public Element getState() {
    return saveState(myProject.getName());
  }



  @Override
  public void loadState(Element el) {
    Course loadedCourse = new Course();
    List<Lesson> loadedLessons = new ArrayList<Lesson>();
    for (Element lessonElement : el.getChildren()) {
      Lesson lesson = new Lesson();
      lesson.setName(lessonElement.getAttributeValue("name"));
      List<Task> loadedTasks = new ArrayList<Task>();
      for (Element taskElement : lessonElement.getChildren()) {
        Task task = new Task();
        task.setName(taskElement.getAttributeValue("name"));
        task.setText(taskElement.getAttributeValue("text"));
        task.setTestFile(taskElement.getAttributeValue("testFile"));
        List<TaskFile> taskFiles = new ArrayList<TaskFile>();
        for (Element taskFileElement : taskElement.getChildren()) {
          TaskFile taskFile = new TaskFile();
          taskFile.setName(taskFileElement.getAttributeValue("name"));
          try {
            taskFile.setLineNum(taskFileElement.getAttribute("myLineNum").getIntValue());
            List<Window> windows = new ArrayList<Window>();
            for (Element windowElement:taskFileElement.getChildren()) {
              Window window = new Window();
              window.setLine(windowElement.getAttribute("line").getIntValue());
              window.setStart(windowElement.getAttribute("start").getIntValue());
              window.setOffsetInLine(windowElement.getAttribute("myOffsetInLine").getIntValue());
              window.setText(windowElement.getAttributeValue("text"));
              window.setHint(windowElement.getAttributeValue("hint"));
              window.setPossibleAnswer(windowElement.getAttributeValue("possibleAnswer"));
              window.setResolveStatus(windowElement.getAttribute("myResolveStatus").getBooleanValue());
              windows.add(window);
            }
            taskFile.setWindows(windows);
          }
          catch (DataConversionException e) {
            e.printStackTrace();
          }
          taskFiles.add(taskFile);
        }
        task.setTaskFiles(taskFiles);
        loadedTasks.add(task);
      }
      lesson.setTaskList(loadedTasks);
      loadedLessons.add(lesson);
    }
    loadedCourse.setLessons(loadedLessons);
    loadedCourse.setName("name");
    myCourse = loadedCourse;
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
    return "StudyTaskManager";
  }

  public static StudyTaskManager getInstance(Project project) {
    StudyTaskManager item = myTaskManagers.get(project.getBasePath());
    if (item != null) {
      return item;
    }
    StudyTaskManager taskManager = new StudyTaskManager(project);
    return taskManager;
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
