// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine.events;

import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.SuspendManager;
import com.intellij.debugger.engine.SuspendManagerUtil;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.sun.jdi.ObjectCollectedException;
import com.sun.jdi.ThreadReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class DebuggerContextCommandImpl extends SuspendContextCommandImpl {
  private static final Logger LOG = Logger.getInstance(DebuggerContextCommandImpl.class);

  private final DebuggerContextImpl myDebuggerContext;
  private final ThreadReferenceProxyImpl myCustomThread; // thread to perform command in
  private SuspendContextImpl myCustomSuspendContext;

  protected DebuggerContextCommandImpl(@NotNull DebuggerContextImpl debuggerContext) {
    this(debuggerContext, null);
  }

  protected DebuggerContextCommandImpl(@NotNull DebuggerContextImpl debuggerContext, @Nullable ThreadReferenceProxyImpl customThread) {
    super(debuggerContext.getSuspendContext());
    myDebuggerContext = debuggerContext;
    myCustomThread = customThread;
  }

  @Nullable
  @Override
  public SuspendContextImpl getSuspendContext() {
    if (myCustomSuspendContext == null) {
      myCustomSuspendContext = super.getSuspendContext();
      ThreadReferenceProxyImpl thread = getThread();
      if (myCustomThread != null &&
          (myCustomSuspendContext == null || myCustomSuspendContext.isResumed() || !myCustomSuspendContext.suspends(thread))) {
        myCustomSuspendContext = SuspendManagerUtil.findContextByThread(myDebuggerContext.getDebugProcess().getSuspendManager(), thread);
      }
    }
    return myCustomSuspendContext;
  }

  private ThreadReferenceProxyImpl getThread() {
    return myCustomThread != null ? myCustomThread : myDebuggerContext.getThreadProxy();
  }

  public final DebuggerContextImpl getDebuggerContext() {
    return myDebuggerContext;
  }

  @Override
  public final void contextAction(@NotNull SuspendContextImpl suspendContext) {
    SuspendManager suspendManager = myDebuggerContext.getDebugProcess().getSuspendManager();
    ThreadReferenceProxyImpl thread = getThread();
    boolean isSuspendedByContext;
    try {
      isSuspendedByContext = suspendManager.isSuspended(thread);
    }
    catch (ObjectCollectedException ignored) {
      notifyCancelled();
      return;
    }
    if (isSuspendedByContext) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Context thread " + suspendContext.getThread());
        LOG.debug("Debug thread" + thread);
      }
      threadAction(suspendContext);
    }
    else {
      // no suspend context currently available
      SuspendContextImpl suspendContextForThread = myCustomThread != null ? suspendContext :
                                                   SuspendManagerUtil.findContextByThread(suspendManager, thread);
      if (suspendContextForThread != null && thread.status() != ThreadReference.THREAD_STATUS_ZOMBIE) {
        suspendContextForThread.postponeCommand(this);
      }
      else {
        notifyCancelled();
      }
    }
  }

  /**
   * @deprecated override {@link #threadAction(SuspendContextImpl)}
   */
  @Deprecated
  public void threadAction() {
    throw new AbstractMethodError();
  }

  public void threadAction(@NotNull SuspendContextImpl suspendContext) {
    //noinspection deprecation
    threadAction();
  }
}
