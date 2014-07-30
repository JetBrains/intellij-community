package ru.compscicenter.edide;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.xmlb.XmlSerializer;
import icons.StudyIcons;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.compscicenter.edide.course.Course;
import ru.compscicenter.edide.course.Lesson;
import ru.compscicenter.edide.course.Task;
import ru.compscicenter.edide.course.TaskFile;
import ru.compscicenter.edide.ui.StudyCondition;
import ru.compscicenter.edide.ui.StudyToolWindowFactory;

import javax.swing.*;
import java.util.HashMap;
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
  public static final String COURSE_ELEMENT = "courseElement";
  private static Map<String, StudyTaskManager> myTaskManagers = new HashMap<String, StudyTaskManager>();
  private static Map<String, String> myDeletedShortcuts = new HashMap<String, String>();
  private final Project myProject;
  private Course myCourse;


  public void setCourse(Course course) {
    myCourse = course;
  }

  private StudyTaskManager(Project project) {
    myTaskManagers.put(project.getBasePath(), this);
    myProject = project;
  }


  public Course getCourse() {
    return myCourse;
  }

  @Nullable
  @Override
  public Element getState() {
    Element el = new Element("taskManager");
    if (myCourse != null) {
      Element courseElement = new Element(COURSE_ELEMENT);
      XmlSerializer.serializeInto(myCourse, courseElement);
      el.addContent(courseElement);
    }
    return el;
  }

  @Override
  public void loadState(Element el) {
    myCourse = XmlSerializer.deserialize(el.getChild(COURSE_ELEMENT), Course.class);
    if (myCourse != null) {
      myCourse.init(true);
    }
  }

  @Override
  public void projectOpened() {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            if (myCourse != null) {
              UISettings.getInstance().HIDE_TOOL_STRIPES = false;
              UISettings.getInstance().fireUISettingsChanged();
              final ToolWindow newWindow = ToolWindowManager.getInstance(myProject).getToolWindow("StudyToolWindow");
              if (newWindow != null) {
                newWindow.getContentManager().removeAllContents(false);
                StudyToolWindowFactory factory = new StudyToolWindowFactory();
                factory.createToolWindowContent(myProject, newWindow);
                newWindow.setIcon(StudyIcons.ShortcutReminder);
                newWindow.show(null);
              }
              addShortcut("ctrl pressed PERIOD", "NextWindow");
              addShortcut("ctrl pressed COMMA", "PrevWindowAction");
              addShortcut("ctrl pressed 7", "ShowHintAction");
            }
          }
        });
      }
    });
  }

  private void addShortcut(String shortcutString, String actionIdString) {
    Keymap keymap = KeymapManager.getInstance().getActiveKeymap();
    Shortcut studyActionShortcut = new KeyboardShortcut(KeyStroke.getKeyStroke(shortcutString), null);
    String[] actionsIds = keymap.getActionIds(studyActionShortcut);
    for (String actionId : actionsIds) {
      myDeletedShortcuts.put(actionId, shortcutString);
      keymap.removeShortcut(actionId, studyActionShortcut);
    }
    keymap.addShortcut(actionIdString, studyActionShortcut);
  }

  @Override
  public void projectClosed() {
    StudyCondition.VALUE = false;
    if (myCourse != null) {
      ToolWindowManager.getInstance(myProject).getToolWindow(StudyToolWindowFactory.STUDY_TOOL_WINDOW).getContentManager()
        .removeAllContents(false);
      if (!myDeletedShortcuts.isEmpty()) {
        for (Map.Entry<String, String> shortcut : myDeletedShortcuts.entrySet()) {
          Keymap keymap = KeymapManager.getInstance().getActiveKeymap();
          Shortcut actionShortcut = new KeyboardShortcut(KeyStroke.getKeyStroke(shortcut.getValue()), null);
          keymap.addShortcut(shortcut.getKey(), actionShortcut);
        }
      }
    }
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
    return new StudyTaskManager(project);
  }

  private int getIndex(String fullName, String logicalName) {
    return Integer.parseInt(fullName.substring(logicalName.length())) - 1;
  }

  public TaskFile getTaskFile(VirtualFile file) {
    VirtualFile fileParent = file.getParent();
    if (fileParent != null) {
      String taskDirName = fileParent.getName();
      if (taskDirName.contains(Task.TASK_DIR)) {
        VirtualFile lessonDir = fileParent.getParent();
        if (lessonDir != null) {
          String lessonDirName = lessonDir.getName();
          int lessonIndex = getIndex(lessonDirName, Lesson.LESSON_DIR);
          Lesson lesson = myCourse.getLessons().get(lessonIndex);
          int taskIndex = getIndex(taskDirName, Task.TASK_DIR);
          Task task = lesson.getTaskList().get(taskIndex);
          return task.getFile(file.getName());
        }
      }
    }
    return null;
  }
}
