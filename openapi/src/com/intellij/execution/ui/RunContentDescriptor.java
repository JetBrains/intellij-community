/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution.ui;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.content.Content;

import javax.swing.*;

public class RunContentDescriptor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.ui.RunContentDescriptor");

  private final ExecutionConsole myExecutionConsole;
  private final ProcessHandler myProcessHandler;
  private final JComponent myComponent;
  private final String myDisplayName;
  /**
   * Used to hack {@link com.intellij.execution.runners.RestartAction}
   */
  private Content myContent;

  public RunContentDescriptor(final ExecutionConsole executionConsole,
                              final ProcessHandler processHandler, final JComponent component, final String displayName) {
    LOG.assertTrue(executionConsole != null || ApplicationManager.getApplication().isUnitTestMode());
    myExecutionConsole = executionConsole;
    myProcessHandler = processHandler;
    myComponent = component;
    myDisplayName = displayName;
  }

  public ExecutionConsole getExecutionConsole() {
    return myExecutionConsole;
  }

  public void dispose() {
    myExecutionConsole.dispose();
  }

  public ProcessHandler getProcessHandler() {
    return myProcessHandler;
  }

  public boolean isContentReuseProhibited() {
    return false;
  }

  public JComponent getComponent() {
    return myComponent;
  }

  public String getDisplayName() {
    return myDisplayName;
  }

  /**
   * @see #myContent
   */
  public void setAttachedContent(final Content content) {
    myContent = content;
  }

  /**
   * @see #myContent
   */
  public Content getAttachedContent() {
    return myContent;
  }
}
