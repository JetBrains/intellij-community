package com.intellij.debugger.engine.events;

import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.SuspendManager;
import com.intellij.debugger.engine.SuspendManagerUtil;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.sun.jdi.ObjectCollectedException;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public abstract class DebuggerContextCommandImpl extends SuspendContextCommandImpl {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.engine.events.DebuggerContextCommandImpl");

  private final DebuggerContextImpl myDebuggerContext;

  protected DebuggerContextCommandImpl(DebuggerContextImpl debuggerContext) {
    super(debuggerContext.getSuspendContext());
    myDebuggerContext = debuggerContext;
  }

  public final DebuggerContextImpl getDebuggerContext() {
    return myDebuggerContext;
  }

  public final void contextAction() throws Exception {
    final SuspendManager suspendManager = myDebuggerContext.getDebugProcess().getSuspendManager();

    final ThreadReferenceProxyImpl debuggerContextThread = myDebuggerContext.getThreadProxy();
    final boolean isSuspendedByContext;
    try {
      isSuspendedByContext = suspendManager.isSuspended(debuggerContextThread);
    }
    catch (ObjectCollectedException e) {
      notifyCancelled();
      return;
    }
    if (isSuspendedByContext) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Context thread " + getSuspendContext().getThread());
        LOG.debug("Debug thread" + debuggerContextThread);
      }
      threadAction();
    }
    else {
      // there are no suspend context currently registered
      SuspendContextImpl suspendContextForThread = SuspendManagerUtil.findContextByThread(suspendManager, debuggerContextThread);
      if(suspendContextForThread != null) {
        suspendContextForThread.postponeCommand(this);
      }
      else {
        notifyCancelled();
      }
    }
  }

  abstract public void threadAction ();
}
