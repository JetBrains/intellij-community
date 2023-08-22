// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.events;

import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.managerThread.SuspendContextCommand;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Performs contextAction when evaluation is available in suspend context
 */
public abstract class SuspendContextCommandImpl extends DebuggerCommandImpl {
  private static final Logger LOG = Logger.getInstance(SuspendContextCommand.class);

  private final SuspendContextImpl mySuspendContext;

  protected SuspendContextCommandImpl(@Nullable SuspendContextImpl suspendContext) {
    mySuspendContext = suspendContext;
  }

  public void contextAction(@NotNull SuspendContextImpl suspendContext) throws Exception {
    throw new AbstractMethodError();
  }

  @Override
  public final void action() throws Exception {
    if (LOG.isDebugEnabled()) {
      LOG.debug("trying " + this);
    }

    final SuspendContextImpl suspendContext = getSuspendContext();
    if (suspendContext == null) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("skip processing - context is null " + this);
      }
      notifyCancelled();
      return;
    }

    if (suspendContext.myInProgress) {
      suspendContext.postponeCommand(this);
    }
    else {
      try {
        if (!suspendContext.isResumed()) {
          suspendContext.myInProgress = true;
          contextAction(suspendContext);
        }
        else {
          notifyCancelled();
        }
      }
      finally {
        suspendContext.myInProgress = false;
        if (suspendContext.isResumed()) {
          for (SuspendContextCommandImpl postponed = suspendContext.pollPostponedCommand(); postponed != null; postponed = suspendContext.pollPostponedCommand()) {
            postponed.notifyCancelled();
          }
        }
        else {
          SuspendContextCommandImpl postponed = suspendContext.pollPostponedCommand();
          if (postponed != null) {
            suspendContext.getDebugProcess().getManagerThread().pushBack(postponed);
          }
        }
      }
    }
  }

  @Nullable
  public SuspendContextImpl getSuspendContext() {
    return mySuspendContext;
  }
}
