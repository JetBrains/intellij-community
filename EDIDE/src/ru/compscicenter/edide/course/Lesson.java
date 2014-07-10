package ru.compscicenter.edide.course;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.DataConversionException;
import org.jdom.Element;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * User: lia
 * Date: 21.06.14
 * Time: 18:40
 */
public class Lesson {
  private String name;
  private List<Task> taskList;
  private Course myCourse = null;
  private int myIndex = -1;
  public static final String LESSON_DIR = "lesson";

  public Element saveState() {
    Element lessonElement = new Element("lessonElement");
    lessonElement.setAttribute("name", name);
    lessonElement.setAttribute("myIndex", String.valueOf(myIndex));
    for (Task task:taskList) {
      lessonElement.addContent(task.saveState());
    }
    return lessonElement;
  }

  public void loadState(Element rootElement) {
    name = rootElement.getAttributeValue("name");
    try {
      myIndex = rootElement.getAttribute("myIndex").getIntValue();
    }
    catch (DataConversionException e) {
      e.printStackTrace();
    }
    List<Element> taskElements = rootElement.getChildren();
    taskList = new ArrayList<Task>(taskElements.size());
    for (Element taskElement:taskElements) {
      Task task =  new Task();
      task.loadState(taskElement);
      taskList.add(task);
    }
  }

  public boolean isResolved() {
    for (Task task:taskList) {
      if (!task.isResolved()) {
        return false;
      }
    }
    return true;
  }
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

  public void create(final Project project, VirtualFile baseDir, File resourseRoot) throws IOException {
    VirtualFile lessonDir =  baseDir.createChildDirectory(this, LESSON_DIR + Integer.toString(myIndex + 1));
    for (int i = 0; i < taskList.size(); i++) {
      taskList.get(i).setIndex(i);
      taskList.get(i).create(project, lessonDir, new File(resourseRoot, lessonDir.getName()));
    }

  }



  public void setParents(Course course) {
    myCourse = course;
    for (Task task: taskList) {
      task.setParents(this);
    }
  }

  public Lesson next() {
    if (myIndex + 1 >= myCourse.getLessons().size()) {
      return null;
    }
    return myCourse.getLessons().get(myIndex + 1);
  }

  public void setIndex(int index) {
    myIndex = index;
  }

  public int getIndex() {
    return myIndex;
  }

  public Lesson prev() {
    if (myIndex - 1 < 0) {
      return null;
    }
    return myCourse.getLessons().get(myIndex - 1);
  }
}
