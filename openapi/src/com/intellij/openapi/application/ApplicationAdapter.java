/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.application;

public abstract class ApplicationAdapter implements ApplicationListener {
  public boolean canExitApplication() {
    return true;
  }

  public void applicationExiting() {
  }

  public void beforeWriteActionStart(Object action) {
  }

  public void writeActionStarted(Object action) {
  }

  public void writeActionFinished(Object action) {
  }
}