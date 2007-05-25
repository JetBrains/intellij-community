package com.intellij.debugger.engine;

import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.openapi.diagnostic.Logger;

import java.util.HashSet;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Set;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class SuspendManagerUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.engine.SuspendManagerUtil");

  public static boolean isEvaluating(SuspendManager suspendManager, ThreadReferenceProxyImpl thread) {
    for (Iterator<SuspendContextImpl> iterator = ((SuspendManagerImpl) suspendManager).getEventContexts().iterator(); iterator.hasNext();) {
      SuspendContextImpl suspendContext = iterator.next();
      if(suspendContext.isEvaluating() && thread.equals(suspendContext.getThread())) {
        return true;
      }
    }
    return false;
  }

  public static SuspendContextImpl findContextByThread(SuspendManager suspendManager, ThreadReferenceProxyImpl thread) {
    for (ListIterator<SuspendContextImpl> iterator = ((SuspendManagerImpl) suspendManager).getPausedContexts().listIterator(); iterator.hasNext();) {
      SuspendContextImpl context = iterator.next();
      if(context.getThread() == thread) {
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

  public static Set<SuspendContextImpl> getSuspendingContexts(SuspendManager suspendManager, ThreadReferenceProxyImpl thread) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    Set<ThreadReferenceProxyImpl> contextThreads = new HashSet<ThreadReferenceProxyImpl>();

    Set<SuspendContextImpl> result = new HashSet<SuspendContextImpl>();

    for (Iterator<SuspendContextImpl> iterator = suspendManager.getEventContexts().iterator(); iterator.hasNext();) {
      final SuspendContextImpl suspendContext = iterator.next();
      if(suspendContext.suspends(thread)) {
        ThreadReferenceProxyImpl contextThread = suspendContext.getThread();
        if (contextThreads.contains(contextThread)) {
          LOG.assertTrue(false, suspendContext);
        }
        contextThreads.add(contextThread);
        result.add(suspendContext);
      }
    }

    return result;
  }

  public static void restoreAfterResume(SuspendContextImpl context, Object resumeData) {
    SuspendManager suspendManager = context.getDebugProcess().getSuspendManager();
    ResumeData data = (ResumeData) resumeData;

    ThreadReferenceProxyImpl thread = context.getThread();
    if(data.myIsFrozen && !suspendManager.isFrozen(thread)) {
      suspendManager.freezeThread(thread);
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("RestoreAfterResume SuspendContextImpl...");
    }
    LOG.assertTrue(context.myResumedThreads == null);

    if(data.myResumedThreads != null) {
      for (Iterator<ThreadReferenceProxyImpl> iterator = data.myResumedThreads.iterator(); iterator.hasNext();) {
        ThreadReferenceProxyImpl resumedThreads = iterator.next();
        resumedThreads.resume();
      }
      context.myResumedThreads = data.myResumedThreads;
    }
  }

  public static Object prepareForResume(SuspendContextImpl context) {
    SuspendManager suspendManager = context.getDebugProcess().getSuspendManager();

    ThreadReferenceProxyImpl thread = context.getThread();

    ResumeData resumeData = new ResumeData(suspendManager.isFrozen(thread), context.myResumedThreads);

    if(resumeData.myIsFrozen) {
      suspendManager.unfreezeThread(thread);
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("Resuming SuspendContextImpl...");
    }
    if(context.myResumedThreads != null) {
      for (Iterator<ThreadReferenceProxyImpl> iterator = context.myResumedThreads.iterator(); iterator.hasNext();) {
        ThreadReferenceProxyImpl resumedThreads = iterator.next();
        resumedThreads.suspend();
      }
      context.myResumedThreads = null;
    }

    return resumeData;
  }

  public static SuspendContextImpl getSuspendContextForThread(SuspendContextImpl suspendContext, ThreadReferenceProxyImpl thread) {
    SuspendContextImpl context = findContextByThread(suspendContext.getDebugProcess().getSuspendManager(), thread);
    return context != null && !context.myInProgress ? context :  suspendContext;
  }

  public static SuspendContextImpl getEvaluatingContext(SuspendManager suspendManager, ThreadReferenceProxyImpl thread) {
    for (SuspendContextImpl suspendContext : ((SuspendManagerImpl)suspendManager).getEventContexts()) {
      if (suspendContext.isEvaluating() && suspendContext.getThread() == thread) {
        return suspendContext;
      }
    }
    return null;
  }

  private static class ResumeData {
    final boolean myIsFrozen;
    final Set<ThreadReferenceProxyImpl> myResumedThreads;

    public ResumeData(boolean isFrozen, Set<ThreadReferenceProxyImpl> resumedThreads) {
      myIsFrozen = isFrozen;
      myResumedThreads = resumedThreads;
    }
  }
}
