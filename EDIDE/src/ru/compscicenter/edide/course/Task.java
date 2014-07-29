package ru.compscicenter.edide.course;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.Nullable;
import ru.compscicenter.edide.StudyUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * User: lia
 * Date: 21.06.14
 * Time: 18:42
 * Implementation of task which contains task files, tests, input file for tests
 */
public class Task {
  public static final String TASK_DIR = "task";
  public String testFile;
  public int testNum;
  public String name;
  public String text;
  public List<TaskFile> taskFiles = new ArrayList<TaskFile>();
  private Lesson myLesson;
  public int myIndex;
  public String input = null;
  public String output = null;

  public int getTestNum() {
    return testNum;
  }

  public List<TaskFile> getTaskFiles() {
    return taskFiles;
  }

  @Transient
  public StudyStatus getStatus() {
    for (TaskFile taskFile : taskFiles) {
      StudyStatus taskFileStatus = taskFile.getStatus();
      if (taskFileStatus == StudyStatus.Unchecked) {
        return StudyStatus.Unchecked;
      }
      if (taskFileStatus == StudyStatus.Failed) {
        return StudyStatus.Failed;
      }
    }
    return StudyStatus.Solved;
  }

  public void setStatus(StudyStatus status) {
    LessonInfo lessonInfo = myLesson.getLessonInfo();
    if (status == StudyStatus.Failed) {
      lessonInfo.setTaskFailed(lessonInfo.getTaskFailed() + 1);
      lessonInfo.setTaskUnchecked(lessonInfo.getTaskUnchecked() - 1);
    }
    if (status == StudyStatus.Solved) {
      lessonInfo.setTaskSolved(lessonInfo.getTaskSolved() + 1);
      lessonInfo.setTaskUnchecked(lessonInfo.getTaskUnchecked() - 1);
    }
    for (TaskFile taskFile : taskFiles) {
      taskFile.setStatus(status);
    }
  }

  public String getTestFile() {
    return testFile;
  }

  public String getText() {
    return text;
  }

  /**
   * Creates task directory in its lesson folder in project user created
   *
   * @param lessonDir    project directory of lesson which task belongs to
   * @param resourceRoot directory where original task file stored
   * @throws IOException
   */
  public void create(VirtualFile lessonDir, File resourceRoot) throws IOException {
    VirtualFile taskDir = lessonDir.createChildDirectory(this, TASK_DIR + Integer.toString(myIndex + 1));
    File newResourceRoot = new File(resourceRoot, taskDir.getName());
    for (int i = 0; i < taskFiles.size(); i++) {
      taskFiles.get(i).setIndex(i);
      taskFiles.get(i).create(taskDir, newResourceRoot);
    }
    File[] filesInTask = newResourceRoot.listFiles();
    if (filesInTask != null) {
      for (File file : filesInTask) {
        String fileName = file.getName();
        if (!isTaskFile(fileName)) {
          File resourceFile = new File(newResourceRoot, fileName);
          File fileInProject = new File(taskDir.getCanonicalPath(), fileName);
          FileUtil.copy(resourceFile, fileInProject);
        }
      }
    }
  }

  private boolean isTaskFile(String fileName) {
    for (TaskFile taskFile : taskFiles) {
      if (taskFile.getName().equals(fileName)) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  public TaskFile getFile(String fileName) {
    for (TaskFile file : taskFiles) {
      if (file.getName().equals(fileName)) {
        return file;
      }
    }
    return null;
  }

  /**
   * Initializes state of task file
   *
   * @param lesson lesson which task belongs to
   */
  public void init(Lesson lesson, boolean isRestarted) {
    myLesson = lesson;
    for (TaskFile taskFile : taskFiles) {
      taskFile.init(this, isRestarted);
    }
  }

  public Task next() {
    Lesson currentLesson = this.myLesson;
    if (myIndex + 1 < myLesson.getTaskList().size()) {
      return myLesson.getTaskList().get(myIndex + 1);
    }
    Lesson nextLesson = currentLesson.next();
    if (nextLesson == null) {
      return null;
    }
    return StudyUtils.getFirst(nextLesson.getTaskList());
  }

  public Task prev() {
    Lesson currentLesson = this.myLesson;
    if (myIndex - 1 >= 0) {
      return myLesson.getTaskList().get(myIndex - 1);
    }
    Lesson prevLesson = currentLesson.prev();
    if (prevLesson == null) {
      return null;
    }
    //getting last task in previous lesson
    return prevLesson.getTaskList().get(prevLesson.getTaskList().size() - 1);
  }

  public void setIndex(int index) {
    myIndex = index;
  }

  public int getIndex() {
    return myIndex;
  }

  public Lesson getLesson() {
    return myLesson;
  }

  public String getInput() {
    return input;
  }

  public String getOutput() {
    return output;
  }


  /**
   * Gets text of resource file such as test input file or task text in needed format
   *
   * @param fileName name of resource file which should exist in task directory
   * @param wrapHTML if it's necessary to wrap text with html tags
   * @return text of resource file wrapped with html tags if necessary
   */
  @Nullable
  public String getResourceText(Project project, String fileName, boolean wrapHTML) {
    String lessonDirName = Lesson.LESSON_DIR + String.valueOf(myLesson.getIndex() + 1);
    String taskDirName = TASK_DIR + String.valueOf(myIndex + 1);
    VirtualFile courseDir = project.getBaseDir().findChild(Course.COURSE_DIR);
    if (courseDir != null) {
      VirtualFile lessonDir = courseDir.findChild(lessonDirName);
      if (lessonDir != null) {
        VirtualFile parentDir = lessonDir.findChild(taskDirName);
        if (parentDir != null) {
          return StudyUtils.getFileText(parentDir.getCanonicalPath(), fileName, wrapHTML);
        }
      }
    }
    return null;
  }
}
