// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine;

import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.ContainerUtil;
import com.sun.jdi.request.EventRequest;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

public final class SuspendManagerUtil {
  private static final Logger LOG = Logger.getInstance(SuspendManagerUtil.class);

  /**
   * Returns suspend context that suspends the thread specified (may be currently evaluating)
   */
  public static @Nullable SuspendContextImpl findContextByThread(@NotNull SuspendManager suspendManager, @Nullable ThreadReferenceProxyImpl thread) {
    SuspendManagerImpl suspendManagerImpl = (SuspendManagerImpl)suspendManager;
    if (thread == null) {
      return ContainerUtil.getFirstItem(suspendManagerImpl.getSuspendAllContexts());
    }

    for (SuspendContextImpl context : suspendManager.getEventContexts()) {
      if ((context.getEventThread() == thread || context.getSuspendPolicy() == EventRequest.SUSPEND_ALL)
          && !context.isExplicitlyResumed(thread)) {
        return context;
      }
    }

    return null;
  }

  public static @Nullable SuspendContextImpl getContextForEvaluation(@NotNull SuspendManager suspendManager) {
    // first try to take the context from the current command, if any
    DebuggerCommandImpl currentCommand = DebuggerManagerThreadImpl.getCurrentCommand();
    SuspendContextImpl currentSuspendContext =
      currentCommand instanceof SuspendContextCommandImpl suspendContextCommand ? suspendContextCommand.getSuspendContext() : null;
    if (currentSuspendContext != null) {
      if (currentSuspendContext.isResumed()) {
        currentSuspendContext.getDebugProcess().logError("Cannot use context " + currentSuspendContext + " for evaluation");
        return null;
      }
      return currentSuspendContext;
    }
    ((SuspendManagerImpl)suspendManager).getDebugProcess().logError(
      "Evaluation should be performed in the SuspendContextCommandImpl, so evaluation context should come from there");
    return suspendManager.getPausedContext();
  }

  public static @NotNull Set<SuspendContextImpl> getSuspendingContexts(@NotNull SuspendManager suspendManager,
                                                                       @NotNull ThreadReferenceProxyImpl thread) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    return StreamEx.of(suspendManager.getEventContexts()).filter(suspendContext -> suspendContext.suspends(thread)).toSet();
  }

  public static @Nullable SuspendContextImpl getSuspendingContext(@NotNull SuspendManager suspendManager, @NotNull ThreadReferenceProxyImpl thread) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    return ContainerUtil.find(suspendManager.getEventContexts(), suspendContext -> suspendContext.suspends(thread));
  }

  @ApiStatus.Internal
  public static @Nullable SuspendContextImpl getPausedSuspendingContext(@NotNull SuspendManager suspendManager,
                                                                        @NotNull ThreadReferenceProxyImpl thread) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    List<SuspendContextImpl> pausedContexts = suspendManager.getPausedContexts();
    SuspendContextImpl context = ContainerUtil.find(pausedContexts, suspendContext ->
      suspendContext.getEventThread() == thread && suspendContext.suspends(thread)
    );
    if (context != null) {
      return context;
    }
    return ContainerUtil.find(pausedContexts, suspendContext -> suspendContext.suspends(thread));
  }

  public static void switchToThreadInSuspendAllContext(@NotNull SuspendContextImpl suspendAllContext, @NotNull ThreadReferenceProxyImpl threadProxy) {
    assert suspendAllContext.getSuspendPolicy() == EventRequest.SUSPEND_ALL;
    DebugProcessImpl debugProcess = suspendAllContext.getDebugProcess();
    suspendAllContext.getManagerThread().schedule(new SuspendContextCommandImpl(suspendAllContext) {
      @Override
      public void contextAction(@NotNull SuspendContextImpl c) {
        DebuggerSession session = debugProcess.getSession();
        DebuggerContextImpl debuggerContext = DebuggerContextImpl.createDebuggerContext(session, suspendAllContext, threadProxy, null);
        DebuggerInvocationUtil.invokeLater(debugProcess.getProject(),
                                           () -> session.getContextManager().setState(debuggerContext, DebuggerSession.State.PAUSED, DebuggerSession.Event.CONTEXT, null));
      }
    });
  }

  public static void restoreAfterResume(SuspendContextImpl context, Object resumeData) {
    SuspendManager suspendManager = context.getDebugProcess().getSuspendManager();
    ResumeData data = (ResumeData)resumeData;

    ThreadReferenceProxyImpl thread = context.getThread();
    if (data.myIsFrozen && !suspendManager.isFrozen(thread)) {
      suspendManager.freezeThread(thread);
    }

    LOG.debug("RestoreAfterResume SuspendContextImpl...");

    if (data.myResumedThreads != null) {
      data.myResumedThreads.forEach(ThreadReferenceProxyImpl::resume);
    }
  }

  public static Object prepareForResume(SuspendContextImpl context) {
    SuspendManager suspendManager = context.getDebugProcess().getSuspendManager();

    ThreadReferenceProxyImpl thread = context.getThread();

    Set<ThreadReferenceProxyImpl> resumedThreads = context.myResumedThreads != null ? Set.copyOf(context.myResumedThreads) : null;
    ResumeData resumeData = new ResumeData(thread != null && suspendManager.isFrozen(thread), resumedThreads);

    if (resumeData.myIsFrozen) {
      suspendManager.unfreezeThread(thread);
    }

    LOG.debug("Resuming SuspendContextImpl...");
    if (context.myResumedThreads != null) {
      resumeData.myResumedThreads.forEach(ThreadReferenceProxyImpl::suspend);
    }

    return resumeData;
  }

  private static class ResumeData {
    final boolean myIsFrozen;
    final Set<ThreadReferenceProxyImpl> myResumedThreads;

    ResumeData(boolean isFrozen, Set<ThreadReferenceProxyImpl> resumedThreads) {
      myIsFrozen = isFrozen;
      myResumedThreads = resumedThreads;
    }
  }
}
