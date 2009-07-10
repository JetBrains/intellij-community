package com.intellij.packaging.ui;

/**
 * @author nik
 */
public abstract class ArtifactProblemQuickFix {
  private String myActionName;

  protected ArtifactProblemQuickFix() {
    this("Fix");
  }

  protected ArtifactProblemQuickFix(String actionName) {
    myActionName = actionName;
  }

  public String getActionName() {
    return myActionName;
  }

  public abstract void performFix();
}
