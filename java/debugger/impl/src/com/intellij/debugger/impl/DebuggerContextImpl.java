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
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import org.jetbrains.annotations.Nullable;


public final class DebuggerContextImpl implements DebuggerContext {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.impl.DebuggerContextImpl");

  public static final DebuggerContextImpl EMPTY_CONTEXT = DebuggerContextImpl.createDebuggerContext((DebuggerSession) null, null, null, null);

  private boolean myInitialized;

  @Nullable
  private final DebuggerSession      myDebuggerSession;
  private final DebugProcessImpl     myDebugProcess;
  private final SuspendContextImpl   mySuspendContext;
  private final ThreadReferenceProxyImpl myThreadProxy;

  private       StackFrameProxyImpl  myFrameProxy;
  private       SourcePosition       mySourcePosition;
  private       PsiElement           myContextElement;

  private DebuggerContextImpl(@Nullable DebuggerSession session, DebugProcessImpl debugProcess, SuspendContextImpl context, ThreadReferenceProxyImpl threadProxy, StackFrameProxyImpl frameProxy, SourcePosition position, PsiElement contextElement, boolean initialized) {
    LOG.assertTrue(frameProxy == null || threadProxy == null || threadProxy == frameProxy.threadProxy());
    LOG.assertTrue(debugProcess == null ? frameProxy == null && threadProxy == null : true);
    myDebuggerSession = session;
    myThreadProxy = threadProxy;
    myFrameProxy = frameProxy;
    myDebugProcess = debugProcess;
    mySourcePosition = position;
    mySuspendContext = context;
    myContextElement = contextElement;
    myInitialized = initialized;
  }

  @Nullable
  public DebuggerSession getDebuggerSession() {
    return myDebuggerSession;
  }

  public DebugProcessImpl getDebugProcess() {
    return myDebugProcess;
  }

  public ThreadReferenceProxyImpl getThreadProxy() {
    return myThreadProxy;
  }

  public SuspendContextImpl getSuspendContext() {
    return mySuspendContext;
  }

  public Project getProject() {
    return myDebugProcess != null ? myDebugProcess.getProject() : null;
  }

  @Nullable
  public StackFrameProxyImpl getFrameProxy() {
    LOG.assertTrue(myInitialized);
    return myFrameProxy;
  }

  public SourcePosition getSourcePosition() {
    LOG.assertTrue(myInitialized);
    return mySourcePosition;
  }

  public PsiElement getContextElement() {
    LOG.assertTrue(myInitialized);
    PsiElement contextElement = myContextElement;
    if(contextElement != null && !contextElement.isValid()) {
      myContextElement = ContextUtil.getContextElement(mySourcePosition);
    }
    return myContextElement;
  }

  public EvaluationContextImpl createEvaluationContext(Value thisObject) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    return new EvaluationContextImpl(getSuspendContext(), getFrameProxy(), thisObject);
  }

  public EvaluationContextImpl createEvaluationContext() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    StackFrameProxyImpl frameProxy = getFrameProxy();
    ObjectReference objectReference;
    try {
      objectReference = frameProxy != null ? frameProxy.thisObject() : null;
    }
    catch (EvaluateException e) {
      LOG.info(e);
      objectReference = null;
    }
    return new EvaluationContextImpl(getSuspendContext(), frameProxy, objectReference);
  }

  public static DebuggerContextImpl createDebuggerContext(DebuggerSession session, SuspendContextImpl context, ThreadReferenceProxyImpl threadProxy, StackFrameProxyImpl frameProxy) {
    LOG.assertTrue(frameProxy == null || threadProxy == null || threadProxy == frameProxy.threadProxy());
    LOG.assertTrue(session == null || session.getProcess() != null);
    return new DebuggerContextImpl(session, session != null ? session.getProcess() : null, context, threadProxy, frameProxy, null, null, context == null);
  }

  public void initCaches() {
    if(myInitialized) return;

    myInitialized = true;
    if(myFrameProxy == null) {
      if(myThreadProxy != null) {
        try {
          myFrameProxy = myThreadProxy.frameCount() > 0 ? myThreadProxy.frame(0) : null;
        }
        catch (EvaluateException e) {
        }
      }
    }

    if(myFrameProxy != null) {
      PsiDocumentManager.getInstance(getProject()).commitAndRunReadAction(new Runnable() {
        public void run() {
          if (mySourcePosition == null) {
            mySourcePosition = ContextUtil.getSourcePosition(DebuggerContextImpl.this);
          }
          myContextElement = ContextUtil.getContextElement(mySourcePosition);
        }
      });
    }
  }

  public void setPositionCache(SourcePosition position) {
    LOG.assertTrue(!myInitialized, "Debugger context is initialized. Cannot change caches");
    mySourcePosition = position;
  }

  public boolean isInitialised() {
    return myInitialized;
  }

  public boolean isEvaluationPossible() {
    final DebugProcessImpl debugProcess = getDebugProcess();
    return debugProcess != null && debugProcess.getSuspendManager().getPausedContext() != null;
  }
}