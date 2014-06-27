package ru.compscicenter.edide.course;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;

import java.io.IOException;
import java.util.List;

/**
 * User: lia
 * Date: 21.06.14
 * Time: 18:40
 */
public class Lesson {
  private String name;
  private List<Task> taskList;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public List<Task> getTaskList() {
    return taskList;
  }

  public void setTaskList(List<Task> taskList) {
    this.taskList = taskList;
  }

  public void create(final Project project, VirtualFile baseDir, int index, String resourseRoot) throws IOException {
    VirtualFile lessonDir =  baseDir.createChildDirectory(this, "lesson" + Integer.toString(index));
    for (int i = 0; i < taskList.size(); i++) {
      taskList.get(i).create(project, lessonDir, i + 1, resourseRoot + "/"+lessonDir.getName() );
    }

  }

  public Element saveState() {
    Element lessonElement =  new Element("lesson");
    for (Task task : taskList) {
      lessonElement.addContent(task.saveState());
    }
    return lessonElement;
  }
}
