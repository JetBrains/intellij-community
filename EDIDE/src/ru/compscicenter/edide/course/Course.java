package ru.compscicenter.edide.course;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * User: lia
 * Date: 21.06.14
 */
public class Course {

  private static final Logger LOG = Logger.getInstance(Course.class.getName());
  private static final String COURSE_ELEMENT_NAME = "courseElement";
  private static final String NAME_ATTRIBUTE_NAME = "name";
  private static final String DESCRIPTION_ATTRIBUTE_NAME = "description";
  private static final String RESOURCE_PATH_ATTRIBUTE_NAME = "myResourcePath";
  private List<Lesson> lessons;
  private String description;
  private String name;
  private String myResourcePath = "";
  public static final String COURSE_DIR = "course";
  public static final String HINTS_DIR = "hints";

  public List<Lesson> getLessons() {
    return lessons;
  }

  /**
   * Initializes state of course
   */
  public void init(boolean isRestarted) {
    for (Lesson lesson : lessons) {
      lesson.init(this, isRestarted);
    }
  }


  /**
   * Creates course directory in project user created
   *
   * @param baseDir      project directory
   * @param resourceRoot directory where original course stored
   */
  public void create(final VirtualFile baseDir, final File resourceRoot) {
    ApplicationManager.getApplication().invokeLater(
      new Runnable() {
        @Override
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              try {
                VirtualFile courseDir = baseDir.createChildDirectory(this, COURSE_DIR);
                for (int i = 0; i < lessons.size(); i++) {
                  lessons.get(i).setIndex(i);
                  lessons.get(i).create(courseDir, resourceRoot);
                }
                //we need z because we want this folder shown last in project tree
                baseDir.createChildDirectory(this, "zPlayground");
              }
              catch (IOException e) {
                LOG.error(e);
              }
            }
          });
        }
      });
  }

  public String getName() {
    return name;
  }

  public void setResourcePath(String resourcePath) {
    myResourcePath = resourcePath;
  }

  public String getResourcePath() {
    return myResourcePath;
  }

  /**
   * Saves course state for serialization
   *
   * @return xml element with attributes and content typical for course
   */
  public Element saveState() {
    Element courseElement = new Element(COURSE_ELEMENT_NAME);
    courseElement.setAttribute(NAME_ATTRIBUTE_NAME, name);
    courseElement.setAttribute(DESCRIPTION_ATTRIBUTE_NAME, description);
    courseElement.setAttribute(RESOURCE_PATH_ATTRIBUTE_NAME, myResourcePath);
    for (Lesson lesson : lessons) {
      courseElement.addContent(lesson.saveState());
    }
    return courseElement;
  }

  /**
   * initializes course after project reopening or IDE restart
   *
   * @param courseElement xml element which contains information about course
   */
  public void loadState(Element courseElement) {
    name = courseElement.getAttributeValue(NAME_ATTRIBUTE_NAME);
    description = courseElement.getAttributeValue(DESCRIPTION_ATTRIBUTE_NAME);
    myResourcePath = courseElement.getAttributeValue(RESOURCE_PATH_ATTRIBUTE_NAME);
    List<Element> lessonElements = courseElement.getChildren();
    lessons = new ArrayList<Lesson>(lessonElements.size());
    for (Element lessonElement : lessonElements) {
      Lesson lesson = new Lesson();
      lesson.loadState(lessonElement);
      lessons.add(lesson);
    }
  }
}
