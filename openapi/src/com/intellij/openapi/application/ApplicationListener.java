/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.application;

import java.util.EventListener;

/**
 * Listener for application events.
 */
public interface ApplicationListener extends EventListener {
  /**
   * This method is called to check whether the Application is ready to exit.
   * @return true or false
   */
  boolean canExitApplication();

  /**
   * Is called when application is exiting.
   */
  void applicationExiting();

  /**
   * Is called before action start.
   */
  void beforeWriteActionStart(Object action);

  /**
   * Is called on action start.
   */
  void writeActionStarted(Object action);

  /**
   *  Is called on action finish.
   */
  void writeActionFinished(Object action);
}