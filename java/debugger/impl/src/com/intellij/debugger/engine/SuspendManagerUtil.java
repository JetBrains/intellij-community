// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine;

import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.SmartHashSet;
import com.sun.jdi.request.EventRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.stream.Collectors;

public class SuspendManagerUtil {
  private static final Logger LOG = Logger.getInstance(SuspendManagerUtil.class);

  public static boolean isEvaluating(SuspendManager suspendManager, ThreadReferenceProxyImpl thread) {
    for (SuspendContextImpl suspendContext : suspendManager.getEventContexts()) {
      if (suspendContext.isEvaluating() && thread.equals(suspendContext.getThread())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns suspend context that suspends the thread specified (may be currently evaluating)
   */
  @Nullable
  public static SuspendContextImpl findContextByThread(@NotNull SuspendManager suspendManager, ThreadReferenceProxyImpl thread) {
    for (SuspendContextImpl context : suspendManager.getEventContexts()) {
      if ((context.getThread() == thread || context.getSuspendPolicy() == EventRequest.SUSPEND_ALL)
          && !context.isExplicitlyResumed(thread)){
        return context;
      }
    }

    return null;
  }

  public static void assertSuspendContext(SuspendContextImpl context) {
    if(LOG.isDebugEnabled()) {
      LOG.assertTrue(context.myInProgress, "You can invoke methods only inside commands invoked for SuspendContext");
    }
  }

  @NotNull
  public static Set<SuspendContextImpl> getSuspendingContexts(@NotNull SuspendManager suspendManager, ThreadReferenceProxyImpl thread) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    return suspendManager.getEventContexts().stream()
      .filter(suspendContext -> suspendContext.suspends(thread))
      .collect(Collectors.toCollection(SmartHashSet::new));
  }

  @Nullable
  public static SuspendContextImpl getSuspendingContext(@NotNull SuspendManager suspendManager, ThreadReferenceProxyImpl thread) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    return ContainerUtil.find(suspendManager.getEventContexts(), suspendContext -> suspendContext.suspends(thread));
  }

  public static void restoreAfterResume(SuspendContextImpl context, Object resumeData) {
    SuspendManager suspendManager = context.getDebugProcess().getSuspendManager();
    ResumeData data = (ResumeData)resumeData;

    ThreadReferenceProxyImpl thread = context.getThread();
    if (data.myIsFrozen && !suspendManager.isFrozen(thread)) {
      suspendManager.freezeThread(thread);
    }

    LOG.debug("RestoreAfterResume SuspendContextImpl...");
    LOG.assertTrue(context.myResumedThreads == null);

    if (data.myResumedThreads != null) {
      data.myResumedThreads.forEach(ThreadReferenceProxyImpl::resume);
      context.myResumedThreads = data.myResumedThreads;
    }
  }

  public static Object prepareForResume(SuspendContextImpl context) {
    SuspendManager suspendManager = context.getDebugProcess().getSuspendManager();

    ThreadReferenceProxyImpl thread = context.getThread();

    ResumeData resumeData = new ResumeData(suspendManager.isFrozen(thread), context.myResumedThreads);

    if (resumeData.myIsFrozen) {
      suspendManager.unfreezeThread(thread);
    }

    LOG.debug("Resuming SuspendContextImpl...");
    if (context.myResumedThreads != null) {
      context.myResumedThreads.forEach(ThreadReferenceProxyImpl::suspend);
      context.myResumedThreads = null;
    }

    return resumeData;
  }

  public static SuspendContextImpl getEvaluatingContext(SuspendManager suspendManager, ThreadReferenceProxyImpl thread) {
    for (SuspendContextImpl suspendContext : suspendManager.getEventContexts()) {
      if (!suspendContext.isResumed() && suspendContext.isEvaluating() && suspendContext.getThread() == thread) {
        return suspendContext;
      }
    }
    return null;
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
