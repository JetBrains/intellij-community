package ru.compscicenter.edide.course;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.DataConversionException;
import org.jdom.Element;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * User: lia
 * Date: 21.06.14
 * Time: 18:42
 */
public class Task {
  private static final Logger LOG = Logger.getInstance(Task.class.getName());
  public static final String TASK_DIR = "task";
  private String testFile;
  private String name;
  private String text;
  private List<TaskFile> taskFiles;
  private Lesson myLesson;
  private boolean mySolved = false;
  private int myIndex;
  private String input = null;
  private String output = null;


  public Element saveState() {
    Element taskElement = new Element("task");
    taskElement.setAttribute("testFile", testFile);
    taskElement.setAttribute("name", name);
    taskElement.setAttribute("text", text);
    taskElement.setAttribute("myIndex", String.valueOf(myIndex));
    taskElement.setAttribute("mySolved", String.valueOf(mySolved));
    if (input!= null)
      taskElement.setAttribute("input", input);
    if (output != null)
      taskElement.setAttribute("output", output);
    for (TaskFile file : taskFiles) {
      taskElement.addContent(file.saveState());
    }
    return taskElement;
  }

  public void loadState(Element taskElement) {
    testFile = taskElement.getAttributeValue("testFile");
    name = taskElement.getAttributeValue("name");
    text =taskElement.getAttributeValue("text");
    input = taskElement.getAttributeValue("input");
    output = taskElement.getAttributeValue("output");
    try {
      mySolved = taskElement.getAttribute("mySolved").getBooleanValue();
      myIndex = taskElement.getAttribute("myIndex").getIntValue();
    }
    catch (DataConversionException e) {
      e.printStackTrace();
    }

    List<Element> taskFileElements = taskElement.getChildren();
    taskFiles = new ArrayList<TaskFile>(taskFileElements.size());
    for (Element taskFileElement:taskFileElements) {
      TaskFile taskFile = new TaskFile();
      taskFile.loadState(taskFileElement);
      taskFiles.add(taskFile);
    }
  }

  public boolean isSolved() {
    return mySolved;
  }

  public void setSolved(boolean solved) {
    mySolved = solved;
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

  public void setTestFile(String testFile) {
    this.testFile = testFile;
  }

  public void setTaskFiles(List<TaskFile> taskFiles) {
    this.taskFiles = taskFiles;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text;
  }

  public void create(VirtualFile baseDir, File resourceRoot) throws IOException {
    VirtualFile taskDir = baseDir.createChildDirectory(this, TASK_DIR + Integer.toString(myIndex + 1));
    File newResourceRoot = new File(resourceRoot, taskDir.getName());
    for (int i = 0; i < taskFiles.size(); i++) {
      taskFiles.get(i).setIndex(i);
      taskFiles.get(i).create(taskDir, newResourceRoot);
    }
    File[] filesInTask = newResourceRoot.listFiles();
    if (filesInTask != null) {
      for (File file : filesInTask) {
        for (TaskFile taskFile : taskFiles) {
          if (!file.getName().equals(taskFile.getName())) {
            FileUtil.copy(new File(newResourceRoot, file.getName()), new File(taskDir.getCanonicalPath(), file.getName()));
          }
        }
      }
    }
  }

  public TaskFile getFile(String fileName) {
    for (TaskFile file : taskFiles) {
      if (file.getName().equals(fileName)) {
        return file;
      }
    }
    return null;
  }


  public void setParents(Lesson lesson) {
    myLesson = lesson;
    for (TaskFile tasFile : taskFiles) {
      tasFile.init(this);
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
    return nextLesson.getTaskList().iterator().next();
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

  public Task prev() {
    Lesson currentLesson = this.myLesson;
    if (myIndex - 1 >= 0) {
      return myLesson.getTaskList().get(myIndex - 1);
    }
    Lesson prevLesson = currentLesson.prev();
    if (prevLesson == null) {
      return null;
    }
    return prevLesson.getTaskList().get(prevLesson.getTaskList().size() - 1);
  }

  public String getInput() {
    return input;
  }

  public String getOutput() {
    return output;
  }

  //you should check if there is such resourceFile
  public String getResourceText(Project project, String fileName, boolean wrapHTML) {
    String lessonDirName = Lesson.LESSON_DIR + String.valueOf(myLesson.getIndex() + 1);
    String taskDirName = TASK_DIR + String.valueOf(myIndex + 1);
    BufferedReader reader = null;
    try {
      VirtualFile parentDir = project.getBaseDir().findChild(Course.COURSE_DIR).findChild(lessonDirName).findChild(taskDirName);
      File inputFile = new File(parentDir.getCanonicalPath(), fileName);
      StringBuilder taskText = new StringBuilder();
      if (wrapHTML) {
        taskText.append("<html>");
      }

      reader = new BufferedReader(new InputStreamReader(new FileInputStream(inputFile)));
      String line =  null;
      while ((line = reader.readLine()) != null) {
        taskText.append(line);
        if (wrapHTML) {
          taskText.append("<br>");
        }
      }
      if (wrapHTML) {
        taskText.append("</html>");
      }
      return taskText.toString();
    }
    catch (FileNotFoundException e) {
      e.printStackTrace();
    }
    catch (IOException e) {
      e.printStackTrace();
    }
    catch (NullPointerException e) {
      LOG.error("not valid project structure'");
    }
    finally {
      if (reader != null) {
        try {
          reader.close();
        }
        catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
    return null;
  }

}
