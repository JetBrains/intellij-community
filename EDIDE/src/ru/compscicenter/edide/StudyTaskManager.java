package ru.compscicenter.edide;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.xmlb.XmlSerializer;
import icons.StudyIcons;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.compscicenter.edide.actions.NextWindowAction;
import ru.compscicenter.edide.actions.PrevWindowAction;
import ru.compscicenter.edide.actions.ShowHintAction;
import ru.compscicenter.edide.course.Course;
import ru.compscicenter.edide.course.Lesson;
import ru.compscicenter.edide.course.Task;
import ru.compscicenter.edide.course.TaskFile;
import ru.compscicenter.edide.ui.StudyCondition;
import ru.compscicenter.edide.ui.StudyToolWindowFactory;

import javax.swing.*;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of class which contains all the information
 * about study in context of current project
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
public class StudyTaskManager implements ProjectComponent, PersistentStateComponent<Element>, DumbAware {
  public static final String COURSE_ELEMENT = "courseElement";
  private static Map<String, StudyTaskManager> myTaskManagers = new HashMap<String, StudyTaskManager>();
  private static Map<String, String> myDeletedShortcuts = new HashMap<String, String>();
  private final Project myProject;
  private Course myCourse;
  private FileCreatedListener myListener;


  public void setCourse(Course course) {
    myCourse = course;
  }

  private StudyTaskManager(@NotNull final Project project) {
    myTaskManagers.put(project.getBasePath(), this);
    myProject = project;
  }


  @Nullable
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
    ApplicationManager.getApplication().invokeLater(new DumbAwareRunnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new DumbAwareRunnable() {
          @Override
          public void run() {
            if (myCourse != null) {
              UISettings.getInstance().HIDE_TOOL_STRIPES = false;
              UISettings.getInstance().fireUISettingsChanged();
              ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
              String toolWindowId = StudyToolWindowFactory.STUDY_TOOL_WINDOW;
              //TODO:decide smth with tool window position
              try {
                Method method = toolWindowManager.getClass().getDeclaredMethod("registerToolWindow", String.class,
                                                                               JComponent.class,
                                                                               ToolWindowAnchor.class,
                                                                               boolean.class, boolean.class, boolean.class);
                method.setAccessible(true);
                method.invoke(toolWindowManager, toolWindowId, null, ToolWindowAnchor.LEFT, true, true, true);
              }
              catch (Exception e) {
                toolWindowManager
                  .registerToolWindow(toolWindowId, true, ToolWindowAnchor.RIGHT, myProject, true);
              }

              final ToolWindow studyToolWindow = toolWindowManager.getToolWindow(toolWindowId);
              if (studyToolWindow != null) {
                StudyUtils.updateStudyToolWindow(myProject);
                studyToolWindow.setIcon(StudyIcons.ShortcutReminder);
                studyToolWindow.show(null);
              }
              addShortcut(NextWindowAction.SHORTCUT, NextWindowAction.ACTION_ID);
              addShortcut(PrevWindowAction.SHORTCUT, PrevWindowAction.ACTION_ID);
              addShortcut(ShowHintAction.SHORTCUT, ShowHintAction.ACTION_ID);
              addShortcut(NextWindowAction.SHORTCUT2, NextWindowAction.ACTION_ID);
            }
          }
        });
      }
    });
  }

  private void addShortcut(@NotNull final String shortcutString, @NotNull final String actionIdString) {
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

  public static StudyTaskManager getInstance(@NotNull final Project project) {
    StudyTaskManager item = myTaskManagers.get(project.getBasePath());
    return item != null ? item : new StudyTaskManager(project);
  }



  @Nullable
  public TaskFile getTaskFile(@NotNull final VirtualFile file) {
    if (myCourse == null) {
      return null;
    }
    VirtualFile taskDir = file.getParent();
    if (taskDir != null) {
      String taskDirName = taskDir.getName();
      if (taskDirName.contains(Task.TASK_DIR)) {
        VirtualFile lessonDir = taskDir.getParent();
        if (lessonDir != null) {
          String lessonDirName = lessonDir.getName();
          int lessonIndex = StudyUtils.getIndex(lessonDirName, Lesson.LESSON_DIR);
          Lesson lesson = myCourse.getLessons().get(lessonIndex);
          int taskIndex = StudyUtils.getIndex(taskDirName, Task.TASK_DIR);
          Task task = lesson.getTaskList().get(taskIndex);
          return task.getFile(file.getName());
        }
      }
    }
    return null;
  }
}
