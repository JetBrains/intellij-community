/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger;

import com.intellij.debugger.engine.DebugProcessListener;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilder;
import com.intellij.debugger.ui.tree.DebuggerTreeNode;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.actionSystem.DataContext;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Apr 29, 2004
 * Time: 6:33:26 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class DebuggerManager implements ProjectComponent, JDOMExternalizable{
  public static DebuggerManager getInstance(Project project) {
    return project.getComponent(DebuggerManager.class);
  }

  public abstract DebugProcess getDebugProcess(ProcessHandler processHandler);

  public abstract void addDebugProcessListener   (ProcessHandler processHandler, DebugProcessListener listener);
  public abstract void removeDebugProcessListener(ProcessHandler processHandler, DebugProcessListener listener);

  public abstract boolean isDebuggerManagerThread();
}
