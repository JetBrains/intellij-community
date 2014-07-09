package ru.compscicenter.edide.course;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * User: lia
 * Date: 21.06.14
 * Time: 18:42
 */
public class Task {
  private static final Logger LOG = Logger.getInstance(Task.class.getName());
  private String testFile;
  private String name;
  private String text;
  private List<TaskFile> taskFiles;
  private Lesson myLesson;
  private boolean mySolved = false;
  private int myIndex;


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

  public void create(Project project, VirtualFile baseDir, File resourseRoot) throws IOException {
    VirtualFile taskDir = baseDir.createChildDirectory(this, "task" + Integer.toString(myIndex + 1));
    File newResourceRoot = new File(resourseRoot, taskDir.getName());
    for (int i = 0; i < taskFiles.size(); i++) {
      taskFiles.get(i).setIndex(i);
      taskFiles.get(i).create(project, taskDir, newResourceRoot);
    }
    FileUtil.copy(new File(newResourceRoot, text), new File(taskDir.getCanonicalPath(), text));
    FileUtil.copy(new File(newResourceRoot, testFile), new File(taskDir.getCanonicalPath(), testFile));
  }

  public TaskFile getFile(String fileName) {
    for (TaskFile file : taskFiles) {
      if (file.getName().equals(fileName)) {
        return file;
      }
    }
    return null;
  }

  public Element saveState() {
    Element taskElement = new Element("task");
    taskElement.setAttribute("testFile", testFile);
    taskElement.setAttribute("name", name);
    //TODO:replace with real text, not fileName
    taskElement.setAttribute("text", text);
    for (TaskFile file : taskFiles) {
      taskElement.addContent(file.saveState());
    }
    return taskElement;
  }

  public void setParents(Lesson lesson) {
    myLesson = lesson;
    for (TaskFile tasFile : taskFiles) {
      tasFile.setParents(this);
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
}
