/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.project;

public abstract class ProjectManagerAdapter implements ProjectManagerListener {
  public void projectOpened(Project project){
  }

  public void projectClosed(Project project){
  }

  public void projectClosing(Project project) {
  }

  public boolean canCloseProject(Project project){
    return true;
  }
}
