package com.intellij.debugger.engine.events;

import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.openapi.diagnostic.Logger;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Feb 24, 2004
 * Time: 7:01:31 PM
 * Performs contextAction when evaluation is available in suspend context
 */
public abstract class SuspendContextCommandImpl extends DebuggerCommandImpl {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.engine.SuspendContextCommand");
  private final SuspendContextImpl mySuspendContext;

  protected SuspendContextCommandImpl(SuspendContextImpl suspendContext) {
    mySuspendContext = suspendContext;
  }

  public abstract void contextAction() throws Exception;

  public final void action() throws Exception {
    if(LOG.isDebugEnabled()) {
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

    if(suspendContext.myInProgress) {
      suspendContext.postponeCommand(this);
    }
    else {
      try {
        if(!suspendContext.isResumed()) {
          suspendContext.myInProgress = true;
          contextAction();
        }
        else {
          notifyCancelled();
        }
      }
      finally{
        suspendContext.myInProgress = false;
        final SuspendContextCommandImpl postponed = suspendContext.pollPostponedCommand();
        if(postponed != null) {
          suspendContext.getDebugProcess().getManagerThread().invokeLater(postponed);
        }
      }
    }
  }

  public SuspendContextImpl getSuspendContext() {
    return mySuspendContext;
  }
}
