package com.intellij.ide.impl;

import com.intellij.ide.SelectInContext;
import com.intellij.ide.SelectInTarget;

/**
 * @author yole
 */
public class ProjectSubViewSelectInTarget implements SelectInTarget {
  private ProjectViewSelectInTarget myBaseTarget;
  private final String mySubId;
  private final int myWeight;

  public ProjectSubViewSelectInTarget(ProjectViewSelectInTarget target, String subId, int weight) {
    myBaseTarget = target;
    mySubId = subId;
    myWeight = weight;
  }

  public boolean canSelect(SelectInContext context) {
    return myBaseTarget.isSubIdSelectable(mySubId, context);
  }

  public void selectIn(SelectInContext context, boolean requestFocus) {
    myBaseTarget.setSubId(mySubId);
    myBaseTarget.selectIn(context, requestFocus);
  }

  public String getToolWindowId() {
    return myBaseTarget.getToolWindowId();
  }

  public String getMinorViewId() {
    return myBaseTarget.getMinorViewId();
  }

  public float getWeight() {
    return myWeight;
  }

  @Override
  public String toString() {
    return myBaseTarget.getSubIdPresentableName(mySubId);
  }
}
