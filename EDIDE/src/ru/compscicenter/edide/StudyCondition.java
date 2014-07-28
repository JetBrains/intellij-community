package ru.compscicenter.edide;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Condition;

/**
 * author: liana
 * data: 7/25/14.
 */
public  class StudyCondition implements Condition, DumbAware {
  public static boolean myValue = false;
  @Override
  public boolean value(Object o) {
    return myValue;
  }
}
