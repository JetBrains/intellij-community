// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine;

import com.intellij.debugger.PositionManager;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.jdi.VirtualMachineProxy;
import com.intellij.debugger.engine.managerThread.DebuggerManagerThread;
import com.intellij.debugger.requests.RequestManager;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.psi.search.GlobalSearchScope;
import com.sun.jdi.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@ApiStatus.NonExtendable
public interface DebugProcess extends UserDataHolder {
  @NonNls String JAVA_STRATUM = "Java";

  Project getProject();

  RequestManager getRequestsManager();

  @NotNull
  PositionManager getPositionManager();

  /**
   * Get the current VM proxy connected to the process.
   * The VM can change due to a single debug process can be connected to several VMs.
   * Prefer {@link SuspendContextImpl#getVirtualMachineProxy()} when possible.
   */
  @ApiStatus.Obsolete
  VirtualMachineProxy getVirtualMachineProxy();

  void addDebugProcessListener(DebugProcessListener listener, Disposable parentDisposable);

  void addDebugProcessListener(DebugProcessListener listener);

  void removeDebugProcessListener(DebugProcessListener listener);

  /**
   * The usual place to call this method is vmAttachedEvent. No additional actions are needed in this case.
   * If position manager is appended later, when DebugSession is up and running, one might need to call BreakpointManager.updateAllRequests()
   * to ensure that just added position manager was considered when creating breakpoint requests
   *
   * @param positionManager to be appended
   */
  void appendPositionManager(PositionManager positionManager);

  void waitFor();

  void waitFor(long timeout);

  void stop(boolean forceTerminate);

  ExecutionResult getExecutionResult();

  /**
   * Get the current DebuggerManagerThread.
   * The thread can change due to a single debug process can be connected to several VMs.
   * Prefer {@link SuspendContextImpl#getManagerThread()} or
   * {@link com.intellij.debugger.engine.events.DebuggerCommandImpl#getCommandManagerThread()} when possible.
   */
  @ApiStatus.Obsolete
  DebuggerManagerThread getManagerThread();

  Value invokeMethod(EvaluationContext evaluationContext,
                     ObjectReference objRef,
                     Method method,
                     List<? extends Value> args) throws EvaluateException;

  /**
   * Is equivalent to invokeInstanceMethod(evaluationContext, classType, method, args, 0)
   */
  Value invokeMethod(EvaluationContext evaluationContext,
                     ClassType classType,
                     Method method,
                     List<? extends Value> args) throws EvaluateException;

  Value invokeInstanceMethod(EvaluationContext evaluationContext,
                             ObjectReference objRef,
                             Method method,
                             List<? extends Value> args,
                             int invocationOptions) throws EvaluateException;

  ReferenceType findClass(@Nullable EvaluationContext evaluationContext,
                          String name,
                          ClassLoaderReference classLoader) throws EvaluateException;

  ArrayReference newInstance(ArrayType arrayType,
                             int dimension) throws EvaluateException;

  ObjectReference newInstance(EvaluationContext evaluationContext,
                              ClassType classType,
                              Method constructor,
                              List<? extends Value> paramList) throws EvaluateException;

  boolean isAttached();

  boolean isDetached();

  boolean isDetaching();

  /**
   * @return the search scope used by debugger to find sources corresponding to classes being executed
   */
  @NotNull
  GlobalSearchScope getSearchScope();

  void printToConsole(String text);

  ProcessHandler getProcessHandler();
}
