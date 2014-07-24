package ru.compscicenter.edide.course;

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
  private static final String LESSON_ELEMENT_NAME = "lessonElement";
  private static final String NAME_ATTRIBUTE_NAME = "name";
  private static final String INDEX_ATTRIBUTE_NAME = "myIndex";
  private String name;
  private List<Task> taskList;
  private Course myCourse = null;
  private int myIndex = -1;
  public static final String LESSON_DIR = "lesson";

  /**
   * Saves lesson state for serialization
   *
   * @return xml element with attributes and content typical for lesson
   */
  public Element saveState() {
    Element lessonElement = new Element(LESSON_ELEMENT_NAME);
    lessonElement.setAttribute(NAME_ATTRIBUTE_NAME, name);
    lessonElement.setAttribute(INDEX_ATTRIBUTE_NAME, String.valueOf(myIndex));
    for (Task task : taskList) {
      lessonElement.addContent(task.saveState());
    }
    return lessonElement;
  }

  /**
   * initializes lesson after reopening of project or IDE restart
   *
   * @param lessonElement xml element which contains information about lesson
   */
  public void loadState(Element lessonElement) {
    name = lessonElement.getAttributeValue(NAME_ATTRIBUTE_NAME);
    try {
      myIndex = lessonElement.getAttribute(INDEX_ATTRIBUTE_NAME).getIntValue();
      List<Element> taskElements = lessonElement.getChildren();
      taskList = new ArrayList<Task>(taskElements.size());
      for (Element taskElement : taskElements) {
        Task task = new Task();
        task.loadState(taskElement);
        taskList.add(task);
      }
    }
    catch (DataConversionException e) {
      e.printStackTrace();
    }
  }

  public StudyStatus getStatus() {
    for (Task task : taskList) {
      StudyStatus taskStatus = task.getStatus();
      if (taskStatus == StudyStatus.Unchecked || taskStatus == StudyStatus.Failed) {
        return StudyStatus.Unchecked;
      }
    }
    return  StudyStatus.Solved;
  }

  public List<Task> getTaskList() {
    return taskList;
  }


  /**
   * Creates lesson directory in its course folder in project user created
   *
   * @param courseDir    project directory of course
   * @param resourceRoot directory where original lesson stored
   * @throws IOException
   */
  public void create(VirtualFile courseDir, File resourceRoot) throws IOException {
    String lessonDirName = LESSON_DIR + Integer.toString(myIndex + 1);
    VirtualFile lessonDir = courseDir.createChildDirectory(this, lessonDirName);
    for (int i = 0; i < taskList.size(); i++) {
      taskList.get(i).setIndex(i);
      taskList.get(i).create(lessonDir, new File(resourceRoot, lessonDir.getName()));
    }
  }


  /**
   * Initializes state of lesson
   *
   * @param course course which lesson belongs to
   */
  public void init(Course course, boolean isRestarted) {
    myCourse = course;
    for (Task task : taskList) {
      task.init(this, isRestarted);
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
