package ru.compscicenter.edide.ui;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import ru.compscicenter.edide.StudyTaskManager;

/**
 * author: liana
 * data: 7/29/14.
 */
public class StudyCondition implements Condition, DumbAware {
  public static boolean myValue = false;
  @Override
  public boolean value(Object o) {
    if (myValue) {
      return true;
    }
    if (o instanceof Project) {
      Project project = (Project) o;
      StudyTaskManager taskManager = StudyTaskManager.getInstance(project);
      if (taskManager.getCourse() != null) {
        myValue = true;
        return true;
      }
    }
    return false;
  }
}
