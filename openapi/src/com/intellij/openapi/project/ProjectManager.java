/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.project;

import com.intellij.openapi.application.ApplicationManager;

/**
 * Provides project management.
 */
public abstract class ProjectManager {
  /**
   * Gets <code>ProjectManager</code> instance.
   *
   * @return <code>ProjectManager</code> instance
   */
  public static ProjectManager getInstance(){
    return ApplicationManager.getApplication().getComponent(ProjectManager.class);
  }

  /**
   * Adds global listener to all projects
   *
   * @param listener listener to add
   */
  public abstract void addProjectManagerListener(ProjectManagerListener listener);

  /**
   * Removes global listener from all projects.
   *
   * @param listener listener to remove
   */
  public abstract void removeProjectManagerListener(ProjectManagerListener listener);

  /**
   * Adds listener to the specified project.
   *
   * @param project project to add listener to
   * @param listener listener to add
   */
  public abstract void addProjectManagerListener(Project project, ProjectManagerListener listener);

  /**
   * Removes listener from the specified project.
   *
   * @param project project to remove listener from
   * @param listener listener to remove
   */
  public abstract void removeProjectManagerListener(Project project, ProjectManagerListener listener);

  public abstract Project[] getOpenProjects();

  public abstract Project getDefaultProject();
}
