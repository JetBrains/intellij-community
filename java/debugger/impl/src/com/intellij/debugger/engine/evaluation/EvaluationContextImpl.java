/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.debugger.engine.evaluation;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.openapi.project.Project;
import com.sun.jdi.ClassLoaderReference;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class EvaluationContextImpl implements EvaluationContext{
  private final Value myThisObject;
  private final SuspendContextImpl mySuspendContext;
  private final StackFrameProxyImpl myFrameProxy;
  private boolean myAutoLoadClasses = true;
  private ClassLoaderReference myClassLoader;
  
  public EvaluationContextImpl(@NotNull SuspendContextImpl suspendContext, StackFrameProxyImpl frameProxy, @Nullable Value thisObject) {
    myThisObject = thisObject;
    myFrameProxy = frameProxy;
    mySuspendContext = suspendContext;
  }

  @Nullable
  @Override
  public Value getThisObject() {
    return myThisObject;
  }

  @NotNull
  @Override
  public SuspendContextImpl getSuspendContext() {
    return mySuspendContext;
  }

  @Override
  public StackFrameProxyImpl getFrameProxy() {
    return myFrameProxy;
  }

  @NotNull
  @Override
  public DebugProcessImpl getDebugProcess() {
    return getSuspendContext().getDebugProcess();
  }

  public DebuggerManagerThreadImpl getManagerThread() {
    return getDebugProcess().getManagerThread();
  }

  @Override
  public Project getProject() {
    DebugProcessImpl debugProcess = getDebugProcess();
    return debugProcess.getProject();
  }

  @Override
  public EvaluationContextImpl createEvaluationContext(Value value) {
    final EvaluationContextImpl copy = new EvaluationContextImpl(getSuspendContext(), getFrameProxy(), value);
    copy.setAutoLoadClasses(myAutoLoadClasses);
    return copy;
  }

  @Nullable
  @Override
  public ClassLoaderReference getClassLoader() throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if (myClassLoader != null) {
      return myClassLoader;
    }
    return myFrameProxy != null ? myFrameProxy.getClassLoader() : null;
  }

  public void setClassLoader(ClassLoaderReference classLoader) {
    myClassLoader = classLoader;
  }

  public boolean isAutoLoadClasses() {
    return myAutoLoadClasses;
  }

  public void setAutoLoadClasses(final boolean autoLoadClasses) {
    myAutoLoadClasses = autoLoadClasses;
  }
}
