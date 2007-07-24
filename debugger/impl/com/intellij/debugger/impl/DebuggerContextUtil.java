package com.intellij.debugger.impl;

import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.ui.impl.watch.ThreadDescriptorImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class DebuggerContextUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.impl.DebuggerContextUtil");

  public static void setStackFrame(DebuggerStateManager manager, final StackFrameProxyImpl stackFrame) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    DebuggerContextImpl context = manager.getContext();
    if(context == null) return;

    DebuggerContextImpl newContext = DebuggerContextImpl.createDebuggerContext(context.getDebuggerSession(), context.getSuspendContext(), stackFrame.threadProxy(), stackFrame);

    manager.setState(newContext, context.getDebuggerSession().getState(), DebuggerSession.EVENT_REFRESH, null);
  }

  public static void setThread(DebuggerStateManager contextManager, ThreadDescriptorImpl item) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    DebuggerContextImpl context = contextManager.getContext();

    DebuggerContextImpl newContext = DebuggerContextImpl.createDebuggerContext(context.getDebuggerSession(), item.getSuspendContext(), item.getThreadReference(), null);

    contextManager.setState(newContext, context.getDebuggerSession().getState(), DebuggerSession.EVENT_CONTEXT, null);
  }

  public static DebuggerContextImpl createDebuggerContext(DebuggerSession session, SuspendContextImpl suspendContext){
    LOG.assertTrue(session != null);
    return DebuggerContextImpl.createDebuggerContext(session, suspendContext, suspendContext != null ? suspendContext.getThread() : null, null);
  }
}
