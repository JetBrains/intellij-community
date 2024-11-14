// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * Interface DebuggerContextImpl
 * @author Jeka
 */
package com.intellij.debugger.impl;

import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.ContextUtil;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public final class DebuggerContextImpl implements DebuggerContext {
  private static final Logger LOG = Logger.getInstance(DebuggerContextImpl.class);

  public static final DebuggerContextImpl EMPTY_CONTEXT = createDebuggerContext(null, null, null, null);

  private boolean myInitialized;

  @Nullable
  private final DebuggerSession myDebuggerSession;
  private final SuspendContextImpl mySuspendContext;
  private final ThreadReferenceProxyImpl myThreadProxy;

  private StackFrameProxyImpl myFrameProxy;
  private SourcePosition mySourcePosition;
  private PsiElement myContextElement;

  private DebuggerContextImpl(@Nullable DebuggerSession session,
                              @Nullable SuspendContextImpl context,
                              ThreadReferenceProxyImpl threadProxy,
                              StackFrameProxyImpl frameProxy,
                              SourcePosition position,
                              PsiElement contextElement,
                              boolean initialized) {
    LOG.assertTrue(frameProxy == null || threadProxy == null || threadProxy == frameProxy.threadProxy());
    myDebuggerSession = session;
    myThreadProxy = threadProxy;
    myFrameProxy = frameProxy;
    mySourcePosition = position;
    mySuspendContext = context;
    myContextElement = contextElement;
    myInitialized = initialized;
  }

  @Nullable
  public DebuggerSession getDebuggerSession() {
    return myDebuggerSession;
  }

  @Nullable
  @Override
  public DebugProcessImpl getDebugProcess() {
    return myDebuggerSession != null ? myDebuggerSession.getProcess() : null;
  }

  @Nullable
  public ThreadReferenceProxyImpl getThreadProxy() {
    return myThreadProxy;
  }

  @Override
  public SuspendContextImpl getSuspendContext() {
    return mySuspendContext;
  }

  @Override
  public Project getProject() {
    return myDebuggerSession != null ? myDebuggerSession.getProject() : null;
  }

  @Override
  @Nullable
  public StackFrameProxyImpl getFrameProxy() {
    LOG.assertTrue(myInitialized);
    return myFrameProxy;
  }

  public @Nullable DebuggerManagerThreadImpl getManagerThread() {
    if (mySuspendContext != null) return mySuspendContext.getManagerThread();
    DebugProcessImpl debugProcess = getDebugProcess();
    //noinspection UsagesOfObsoleteApi
    return debugProcess != null ? debugProcess.getManagerThread() : null;
  }

  public SourcePosition getSourcePosition() {
    LOG.assertTrue(myInitialized);
    return mySourcePosition;
  }

  public PsiElement getContextElement() {
    LOG.assertTrue(myInitialized);
    PsiElement contextElement = myContextElement;
    if (contextElement != null && !contextElement.isValid()) {
      myContextElement = ContextUtil.getContextElement(mySourcePosition);
    }
    return myContextElement;
  }

  public EvaluationContextImpl createEvaluationContext(Value thisObject) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    return new EvaluationContextImpl(getSuspendContext(), getFrameProxy(), thisObject);
  }

  @Nullable
  public EvaluationContextImpl createEvaluationContext() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    SuspendContextImpl context = getSuspendContext();
    return context != null ? new EvaluationContextImpl(context, getFrameProxy()) : null;
  }

  @NotNull
  public static DebuggerContextImpl createDebuggerContext(@Nullable DebuggerSession session,
                                                          @Nullable SuspendContextImpl context,
                                                          ThreadReferenceProxyImpl threadProxy,
                                                          StackFrameProxyImpl frameProxy) {
    LOG.assertTrue(frameProxy == null || threadProxy == null || threadProxy == frameProxy.threadProxy());
    return new DebuggerContextImpl(session, context, threadProxy, frameProxy, null, null, context == null);
  }

  public void initCaches() {
    if (myInitialized) return;

    myInitialized = true;
    if (myFrameProxy == null) {
      if (myThreadProxy != null) {
        if (mySuspendContext != null && myThreadProxy.equals(mySuspendContext.getThread())) {
          myFrameProxy = mySuspendContext.getFrameProxy();
        }
        else {
          try {
            myFrameProxy = myThreadProxy.frameCount() > 0 ? myThreadProxy.frame(0) : null;
          }
          catch (EvaluateException ignored) {
          }
        }
      }
    }

    if (myFrameProxy != null) {
      if (mySourcePosition == null) {
        mySourcePosition = ContextUtil.getSourcePosition(this);
      }
      myContextElement = ContextUtil.getContextElement(mySourcePosition);
    }
  }

  public void setPositionCache(SourcePosition position) {
    //LOG.assertTrue(!myInitialized, "Debugger context is initialized. Cannot change caches");
    mySourcePosition = position;
  }

  public boolean isInitialised() {
    return myInitialized;
  }

  public boolean isEvaluationPossible() {
    final DebugProcessImpl debugProcess = getDebugProcess();
    return debugProcess != null && debugProcess.isEvaluationPossible();
  }
}