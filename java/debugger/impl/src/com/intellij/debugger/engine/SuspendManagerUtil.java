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
package com.intellij.debugger.engine;

import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.openapi.diagnostic.Logger;

import java.util.HashSet;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Set;

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
    final Set<SuspendContextImpl> result = new HashSet<SuspendContextImpl>();
    for (final SuspendContextImpl suspendContext : suspendManager.getEventContexts()) {
      if (suspendContext.suspends(thread)) {
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
    if (suspendContext == null) {
      return null;
    }
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
