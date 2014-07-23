package ru.compscicenter.edide.course;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.DataConversionException;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;
import ru.compscicenter.edide.StudyUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * User: lia
 * Date: 21.06.14
 * Time: 18:42
 * Implementation of task which contains task files, tests, input file for tests
 */
public class Task {
  private static final String INDEX_ATTRIBUTE_NAME = "myIndex";
  public static final String TASK_DIR = "task";
  private static final String TASK_ELEMENT_NAME = "task";
  private static final String TEST_FILE_ATTRIBUTE_NAME = "testFile";
  private static final String NAME_ATTRIBUTE_NAME = "name";
  private static final String TEXT_ATTRIBUTE_NAME = "text";
  private static final String SOLVED_ATTRIBUTE_NAME = "mySolved";
  private static final String INPUT_ATTRIBUTE_NAME = "input";
  private static final String OUTPUT_ATTRIBUTE_NAME = "output";
  private static final String TEST_NUM_ATTRIBUTE_NAME = "testNum";
  public static final String FAILED_ATTRIBUTE_NAME = "myFailed";
  private String testFile;
  private int testNum;
  private String name;
  private String text;
  private List<TaskFile> taskFiles;
  private Lesson myLesson;
  private boolean mySolved = false;
  private boolean myFailed = false;
  private int myIndex;
  private String input = null;
  private String output = null;

  public int getTestNum() {
    return testNum;
  }

  /**
   * Saves task state for serialization
   *
   * @return xml element with attributes and content typical for task
   */
  public Element saveState() {
    Element taskElement = new Element(TASK_ELEMENT_NAME);
    taskElement.setAttribute(TEST_FILE_ATTRIBUTE_NAME, testFile);
    taskElement.setAttribute(NAME_ATTRIBUTE_NAME, name);
    taskElement.setAttribute(TEXT_ATTRIBUTE_NAME, text);
    taskElement.setAttribute(INDEX_ATTRIBUTE_NAME, String.valueOf(myIndex));
    taskElement.setAttribute(SOLVED_ATTRIBUTE_NAME, String.valueOf(mySolved));
    taskElement.setAttribute(FAILED_ATTRIBUTE_NAME, String.valueOf(myFailed));
    taskElement.setAttribute(TEST_NUM_ATTRIBUTE_NAME, String.valueOf(testNum));
    if (input != null) {
      taskElement.setAttribute(INPUT_ATTRIBUTE_NAME, input);
    }
    if (output != null) {
      taskElement.setAttribute(OUTPUT_ATTRIBUTE_NAME, output);
    }
    for (TaskFile file : taskFiles) {
      taskElement.addContent(file.saveState());
    }
    return taskElement;
  }

  /**
   * initializes task after reopening of project or IDE restart
   *
   * @param taskElement xml element which contains information about task
   */
  public void loadState(Element taskElement) {
    testFile = taskElement.getAttributeValue(TEST_FILE_ATTRIBUTE_NAME);
    name = taskElement.getAttributeValue(NAME_ATTRIBUTE_NAME);
    text = taskElement.getAttributeValue(TEXT_ATTRIBUTE_NAME);
    input = taskElement.getAttributeValue(INPUT_ATTRIBUTE_NAME);
    output = taskElement.getAttributeValue(OUTPUT_ATTRIBUTE_NAME);
    try {
      mySolved = taskElement.getAttribute(SOLVED_ATTRIBUTE_NAME).getBooleanValue();
      myFailed = taskElement.getAttribute(FAILED_ATTRIBUTE_NAME).getBooleanValue();
      myIndex = taskElement.getAttribute(INDEX_ATTRIBUTE_NAME).getIntValue();
      testNum = taskElement.getAttribute(TEST_NUM_ATTRIBUTE_NAME).getIntValue();
      List<Element> taskFileElements = taskElement.getChildren();
      taskFiles = new ArrayList<TaskFile>(taskFileElements.size());
      for (Element taskFileElement : taskFileElements) {
        TaskFile taskFile = new TaskFile();
        taskFile.loadState(taskFileElement);
        taskFiles.add(taskFile);
      }
    }
    catch (DataConversionException e) {
      e.printStackTrace();
    }
  }

  public boolean isFailed() {
    return myFailed;
  }

  public void setFailed(boolean failed) {
    myFailed = failed;
    mySolved = false;
  }

  public boolean isSolved() {
    return mySolved;
  }

  public void setSolved(boolean solved) {
    mySolved = solved;
    myFailed = false;
    for (TaskFile taskFile : taskFiles) {
      taskFile.setSolved();
    }
  }

  public List<TaskFile> getTaskFiles() {
    return taskFiles;
  }

  public boolean isResolved() {
    for (TaskFile taskFile : taskFiles) {
      if (!taskFile.isResolved()) {
        return false;
      }
    }
    return true;
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
