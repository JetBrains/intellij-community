/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.project;

import java.util.EventListener;

/**
 * Listener for Project.
 */
public interface ProjectManagerListener extends EventListener {
  ProjectManagerListener[] EMPTY_ARRAY = new ProjectManagerListener[0];

  /**
   * Invoked on project open.
   *
   * @param project opening project
   */
  void projectOpened(Project project);

  /**
   * Checks whether the project can be closed.
   *
   * @param project project to check
   * @return true or false
   */
  boolean canCloseProject(Project project);

  /**
   * Invoked on project close.
   *
   * @param project closing project
   */
  void projectClosed(Project project);

  /**
   * Invoked on project close before any closing activities
   */
  void projectClosing(Project project);
}
