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
package com.intellij.debugger.engine.events;

import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.SuspendManager;
import com.intellij.debugger.engine.SuspendManagerUtil;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.sun.jdi.ObjectCollectedException;
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
  public final void contextAction() throws Exception {
    SuspendManager suspendManager = myDebuggerContext.getDebugProcess().getSuspendManager();
    boolean isSuspendedByContext;
    try {
      isSuspendedByContext = suspendManager.isSuspended(getThread());
    }
    catch (ObjectCollectedException ignored) {
      notifyCancelled();
      return;
    }
    if (isSuspendedByContext) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Context thread " + getSuspendContext().getThread());
        LOG.debug("Debug thread" + getThread());
      }
      threadAction();
    }
    else {
      // no suspend context currently available
      SuspendContextImpl suspendContextForThread = myCustomThread != null ? getSuspendContext() :
                                                   SuspendManagerUtil.findContextByThread(suspendManager, getThread());
      if (suspendContextForThread != null) {
        suspendContextForThread.postponeCommand(this);
      }
      else {
        notifyCancelled();
      }
    }
  }

  abstract public void threadAction();
}
