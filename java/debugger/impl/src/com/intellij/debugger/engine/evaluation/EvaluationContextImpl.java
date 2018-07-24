// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine.evaluation;

import com.intellij.debugger.EvaluatingComputable;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ThrowableComputable;
import com.sun.jdi.ClassLoaderReference;
import com.sun.jdi.ObjectCollectedException;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class EvaluationContextImpl implements EvaluationContext {
  private static final Logger LOG = Logger.getInstance(EvaluationContextImpl.class);

  private final DebuggerComputableValue myThisObject;
  private final SuspendContextImpl mySuspendContext;
  private final StackFrameProxyImpl myFrameProxy;
  private boolean myAutoLoadClasses = true;
  private ClassLoaderReference myClassLoader;

  private EvaluationContextImpl(@NotNull SuspendContextImpl suspendContext,
                               @Nullable StackFrameProxyImpl frameProxy,
                               @NotNull DebuggerComputableValue thisObjectComputableValue) {
    myThisObject = thisObjectComputableValue;
    myFrameProxy = frameProxy;
    mySuspendContext = suspendContext;
  }

  public EvaluationContextImpl(@NotNull SuspendContextImpl suspendContext,
                               @Nullable StackFrameProxyImpl frameProxy,
                               @NotNull EvaluatingComputable<Value> thisObjectFactory) {
    this(suspendContext, frameProxy, new DebuggerComputableValue(thisObjectFactory));
  }

  public EvaluationContextImpl(@NotNull SuspendContextImpl suspendContext, @Nullable StackFrameProxyImpl frameProxy, @Nullable Value thisObject) {
    this(suspendContext, frameProxy, new DebuggerComputableValue(thisObject));
  }

  public EvaluationContextImpl(@NotNull SuspendContextImpl suspendContext, @Nullable StackFrameProxyImpl frameProxy) {
    this(suspendContext, frameProxy, () -> frameProxy != null ? frameProxy.thisObject() : null);
  }

  @Nullable
  @Override
  @Deprecated
  public Value getThisObject() {
    try {
      return computeThisObject();
    }
    catch (EvaluateException e) {
      LOG.info(e);
    }
    return null;
  }

  @Nullable
  @Override
  public Value computeThisObject() throws EvaluateException {
    return myThisObject.getValue();
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

  public EvaluationContextImpl withAutoLoadClasses(boolean autoLoadClasses) {
    if (myAutoLoadClasses == autoLoadClasses) {
      return this;
    }
    EvaluationContextImpl copy = new EvaluationContextImpl(mySuspendContext, myFrameProxy, myThisObject);
    copy.setAutoLoadClasses(autoLoadClasses);
    return copy;
  }

  @Override
  public void keep(Value value) {
    if (value instanceof ObjectReference) {
      getSuspendContext().keep((ObjectReference)value);
    }
  }

  @Override
  public <T extends Value> T computeAndKeep(@NotNull ThrowableComputable<T, EvaluateException> computable) throws EvaluateException {
    int retries = 10;
    while (true) {
      T res = computable.compute();
      try {
        keep(res);
        return res;
      }
      catch (ObjectCollectedException oce) {
        if (--retries < 0) {
          throw oce;
        }
      }
    }
  }
}
