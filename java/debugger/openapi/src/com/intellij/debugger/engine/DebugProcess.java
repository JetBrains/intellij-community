// Copyright 2000-2017 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
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
