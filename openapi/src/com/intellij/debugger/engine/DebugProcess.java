/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.engine;

import com.intellij.debugger.engine.DebugProcessListener;
import com.intellij.debugger.engine.SuspendContext;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.managerThread.DebuggerManagerThread;
import com.intellij.debugger.engine.jdi.VirtualMachineProxy;
import com.intellij.debugger.requests.RequestManager;
import com.intellij.debugger.ui.tree.render.NodeRendererManager;
import com.intellij.debugger.PositionManager;
import com.intellij.execution.ExecutionResult;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.sun.jdi.*;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Apr 2, 2004
 * Time: 7:17:18 PM
 * To change this template use File | Settings | File Templates.
 */
public interface DebugProcess {
  <T> T    getUserData(Key<T> key);  
  <T> void putUserData(Key<T> key, T value);

  Project getProject();

  NodeRendererManager getNodeRendererManager();

  RequestManager getRequestsManager();

  PositionManager getPositionManager();

  VirtualMachineProxy getVirtualMachineProxy();

  void addDebugProcessListener(DebugProcessListener listener);

  void removeDebugProcessListener(DebugProcessListener listener);

  void appendPositionManager(PositionManager positionManager);

  void waitFor();

  void stop(boolean forceTerminate);

  ExecutionResult getExecutionResult();

  DebuggerManagerThread getManagerThread();

  Value invokeMethod(EvaluationContext evaluationContext,
                     ObjectReference objRef,
                     Method toStringMethod,
                     List emptyList) throws EvaluateException;

  Value invokeMethod(EvaluationContext evaluationContext,
                     ClassType classType,
                     Method method,
                     List args) throws EvaluateException;

  ReferenceType findClass(EvaluationContext evaluationContext,
                          String name,
                          ClassLoaderReference classLoader) throws EvaluateException;

  ArrayReference newInstance(ArrayType arrayType,
                             int dimension) throws EvaluateException;

  ObjectReference newInstance(EvaluationContext evaluationContext,
                              ClassType classType,
                              Method constructor,
                              List paramList) throws EvaluateException;

  boolean isAttached();
}
