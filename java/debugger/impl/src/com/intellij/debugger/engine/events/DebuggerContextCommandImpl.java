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
package com.intellij.debugger.engine.events;

import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.SuspendManager;
import com.intellij.debugger.engine.SuspendManagerUtil;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.sun.jdi.ObjectCollectedException;

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
