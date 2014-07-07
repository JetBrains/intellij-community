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
  private  Lesson myLesson;
  private boolean mySolved = false;


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
    for (TaskFile taskFile:taskFiles) {
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

  public void create(Project project, VirtualFile baseDir, int index, File resourseRoot) throws IOException {
    VirtualFile taskDir = baseDir.createChildDirectory(this, "task" + Integer.toString(index));
    File newResourceRoot = new File(resourseRoot, taskDir.getName());
    for (int i = 0; i < taskFiles.size(); i++) {
      taskFiles.get(i).create(project, taskDir, newResourceRoot);
    }
    FileUtil.copy(new File(newResourceRoot, text), new File(taskDir.getCanonicalPath(), text));
    String systemIndependentName = FileUtil.toSystemIndependentName(testFile);
    final int i = systemIndependentName.lastIndexOf("/");
    if (i > 0) {
      systemIndependentName = systemIndependentName.substring(i + 1);
    }
      VirtualFile ideaDir = project.getBaseDir().findChild(".idea");
      if (ideaDir != null) {
        FileUtil.copy(new File(newResourceRoot, testFile),
                      new File(new File(ideaDir.getCanonicalPath(), "study-tests"),systemIndependentName));
      }

  }

  public TaskFile getFile(String fileName) {
    for (TaskFile file: taskFiles) {
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
    for (TaskFile file: taskFiles) {
      taskElement.addContent(file.saveState());
    }
    return taskElement;
  }

  public void setParents(Lesson lesson) {
    myLesson = lesson;
    for (TaskFile tasFile: taskFiles) {
      tasFile.setParents(this);
    }
  }
}
