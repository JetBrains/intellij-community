package ru.compscicenter.edide.course;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;

import java.io.IOException;
import java.util.List;

/**
 * User: lia
 * Date: 21.06.14
 * Time: 18:42
 */
public class Task {
  private String testFile;
  private String name;
  private String text;
  private List<TaskFile> taskFiles;

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

  public void create(Project project, VirtualFile baseDir, int index, String resourseRoot) throws IOException {
    VirtualFile taskDir = baseDir.createChildDirectory(this, "task" + Integer.toString(index));
    for (int i = 0; i < taskFiles.size(); i++) {
      taskFiles.get(i).create(project, taskDir, resourseRoot+"/"+taskDir.getName());
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
    for (TaskFile file: taskFiles) {
      taskElement.addContent(file.saveState());
    }
    return taskElement;
  }
}
