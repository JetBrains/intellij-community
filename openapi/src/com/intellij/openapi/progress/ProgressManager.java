/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.progress;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;

import javax.swing.*;

public abstract class ProgressManager {
  private static volatile ProgressManager ourCachedInstance = null;
  public static ProgressManager getInstance() {
    ProgressManager instance = ourCachedInstance;
    if( instance == null )
      instance = ourCachedInstance = ApplicationManager.getApplication().getComponent(ProgressManager.class);
    return instance;
  }

  public abstract boolean hasProgressIndicator();
  public abstract boolean hasModalProgressIndicator();

  public abstract void runProcess(Runnable process, ProgressIndicator progress) throws ProcessCanceledException;

  public abstract ProgressIndicator getProgressIndicator();

  public abstract void checkCanceled();

  public abstract void registerFunComponentProvider(ProgressFunComponentProvider provider);
  public abstract void removeFunComponentProvider(ProgressFunComponentProvider provider);
  public abstract JComponent getProvidedFunComponent(Project project, String processId);

  public abstract void setCancelButtonText(String cancelButtonText);
}
