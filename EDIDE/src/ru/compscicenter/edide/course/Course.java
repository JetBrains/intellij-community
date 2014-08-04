package ru.compscicenter.edide.course;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Course {

  private static final Logger LOG = Logger.getInstance(Course.class.getName());
  public static final String PLAYGROUND_DIR = "Playground";
  public List<Lesson> lessons = new ArrayList<Lesson>();
  public String description;
  public String name;
  public String myResourcePath = "";
  public String author;
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

  public String getAuthor() {
    return author;
  }

  /**
   * Creates course directory in project user created
   *
   * @param baseDir      project directory
   * @param resourceRoot directory where original course is stored
   */
  public void create(@NotNull final VirtualFile baseDir, @NotNull final File resourceRoot) {
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
                  Lesson lesson = lessons.get(i);
                  lesson.setIndex(i);
                  lesson.create(courseDir, resourceRoot);
                }
                baseDir.createChildDirectory(this, PLAYGROUND_DIR);
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

  public void setResourcePath(@NotNull final String resourcePath) {
    myResourcePath = resourcePath;
  }

  public String getResourcePath() {
    return myResourcePath;
  }

  public String getDescription() {
    return description;
  }
}
