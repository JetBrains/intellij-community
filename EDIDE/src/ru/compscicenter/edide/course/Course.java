package ru.compscicenter.edide.course;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;

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
  public List<Lesson> lessons = new ArrayList<Lesson>();
  public String description;
  public String name;
  public String myResourcePath = "";
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
}
