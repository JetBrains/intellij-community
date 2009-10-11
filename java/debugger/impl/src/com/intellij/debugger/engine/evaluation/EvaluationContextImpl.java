/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/**
 * User: lex
 * Date: Aug 28, 2003
 * Time: 2:02:29 PM
 */
public final class EvaluationContextImpl implements EvaluationContext{
  private final Value myThisObject;
  private final SuspendContextImpl mySuspendContext;
  private final StackFrameProxyImpl myFrameProxy;
  private boolean myAutoLoadClasses = true;
  
  public EvaluationContextImpl(@NotNull SuspendContextImpl suspendContext, StackFrameProxyImpl frameProxy, Value thisObject) {
    myThisObject = thisObject;
    myFrameProxy = frameProxy;
    mySuspendContext = suspendContext;
  }

  public Value getThisObject() {
    return myThisObject;
  }

  public SuspendContextImpl getSuspendContext() {
    return mySuspendContext;
  }

  public StackFrameProxyImpl getFrameProxy() {
    return myFrameProxy;
  }

  public DebugProcessImpl getDebugProcess() {
    return getSuspendContext().getDebugProcess();
  }

  public Project getProject() {
    DebugProcessImpl debugProcess = getDebugProcess();
    return debugProcess != null ? debugProcess.getProject() : null;
  }

  public EvaluationContextImpl createEvaluationContext(Value value) {
    final EvaluationContextImpl copy = new EvaluationContextImpl(getSuspendContext(), getFrameProxy(), value);
    copy.setAutoLoadClasses(myAutoLoadClasses);
    return copy;
  }

  public ClassLoaderReference getClassLoader() throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    return myFrameProxy != null ? myFrameProxy.getClassLoader() : null;
  }

  public boolean isAutoLoadClasses() {
    return myAutoLoadClasses;
  }

  public void setAutoLoadClasses(final boolean autoLoadClasses) {
    myAutoLoadClasses = autoLoadClasses;
  }
}
