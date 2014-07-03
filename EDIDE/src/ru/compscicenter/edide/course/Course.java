package ru.compscicenter.edide.course;

import com.google.gson.annotations.Expose;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.List;

/**
 * User: lia
 * Date: 21.06.14
 * Time: 19:00
 */
public class Course {

  @Expose
  private List<Lesson> lessons;
  @Expose
  private String description;
  @Expose
  private String name;
  public List<Lesson> getLessons() {
    return lessons;
  }

  public void setParents() {
    for (Lesson lesson:lessons) {
      lesson.setParents(this);
    }
  }

  public void setLessons(List<Lesson> lessons) {
    this.lessons = lessons;
  }

  public void create(final Project project, final VirtualFile baseDir, final String resourseRoot) {
    ApplicationManager.getApplication().invokeLater(
      new Runnable() {
        @Override
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              try {
                //VirtualFile courseDir = baseDir.createChildDirectory(this, "course");
                for (int i = 0; i < lessons.size(); i++) {
                  //lessons.get(i).create(project, courseDir, i + 1, resourseRoot);
                  lessons.get(i).create(project, baseDir, i + 1, resourseRoot);
                }
              }
              catch (IOException e) {
                e.printStackTrace();
              }
            }
          });
        }
      });
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }
}
