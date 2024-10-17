// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.debugger.engine.requests.RequestManagerImpl;
import com.intellij.debugger.impl.DebuggerUtilsAsync;
import com.intellij.debugger.impl.PrioritizedTask;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.containers.ContainerUtil;
import com.sun.jdi.ObjectCollectedException;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.request.EventRequest;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

public class SuspendManagerImpl implements SuspendManager {
  private static final Logger LOG = Logger.getInstance(SuspendManager.class);

  private final AtomicLong mySuspendContextNextId = new AtomicLong();

  private final Deque<SuspendContextImpl> myEventContexts = new ConcurrentLinkedDeque<>();
  /**
   * contexts, paused at breakpoint or another debugger event requests. Note that thread, explicitly paused by user is not considered as
   * "paused at breakpoint" and JDI prohibits data queries on its stack frames
   */
  private final Deque<SuspendContextImpl> myPausedContexts = new ConcurrentLinkedDeque<>();
  private final Set<ThreadReferenceProxyImpl> myFrozenThreads = ConcurrentCollectionFactory.createConcurrentSet();

  protected final Set<ThreadReferenceProxyImpl> myExplicitlyResumedThreads = ConcurrentCollectionFactory.createConcurrentSet();

  private final DebugProcessImpl myDebugProcess;

  private int suspends = 0;

  public SuspendManagerImpl(@NotNull DebugProcessImpl debugProcess) {
    myDebugProcess = debugProcess;
    myDebugProcess.addDebugProcessListener(new DebugProcessAdapterImpl() {
      @Override
      public void processDetached(DebugProcessImpl process, boolean closedByUser) {
        myEventContexts.forEach(Disposer::dispose);
        myEventContexts.clear();
        myPausedContexts.forEach(Disposer::dispose);
        myPausedContexts.clear();
        myFrozenThreads.clear();
      }
    });
  }

  @NotNull
  @Override
  public SuspendContextImpl pushSuspendContext(@MagicConstant(flagsFromClass = EventRequest.class) final int suspendPolicy, int nVotes) {
    SuspendContextImpl suspendContext = new SuspendContextImpl(myDebugProcess, suspendPolicy, nVotes, null, mySuspendContextNextId.incrementAndGet()) {
      @Override
      protected void resumeImpl() {
        LOG.debug("Start resuming...");
        switch (getSuspendPolicy()) {
          case EventRequest.SUSPEND_ALL -> {
            getVirtualMachineProxy().resume();
            LOG.debug("VM resumed ");
          }
          case EventRequest.SUSPEND_EVENT_THREAD -> {
            myFrozenThreads.remove(getEventThread());
            getEventThread().resume();
            if (LOG.isDebugEnabled()) {
              LOG.debug("Thread resumed : " + getEventThread());
            }
          }
          case EventRequest.SUSPEND_NONE -> LOG.debug("None resumed");
        }
        if (LOG.isDebugEnabled()) {
          LOG.debug("Suspends = " + suspends);
        }
      }
    };
    pushContext(suspendContext);
    return suspendContext;
  }

  @NotNull
  @Override
  public SuspendContextImpl pushSuspendContext(final @NotNull EventSet set) {
    SuspendContextImpl suspendContext = new SuspendContextImpl(myDebugProcess, set.suspendPolicy(), set.size(), set, mySuspendContextNextId.incrementAndGet()) {
      @Override
      protected void resumeImpl() {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Start resuming eventSet " + set + " suspendPolicy = " + set.suspendPolicy() + ",size = " + set.size());
        }
        switch (getSuspendPolicy()) {
          case EventRequest.SUSPEND_ALL -> getVirtualMachineProxy().resumedSuspendAllContext();
          case EventRequest.SUSPEND_EVENT_THREAD -> Objects.requireNonNull(getEventThread()).threadWasResumed();
        }
        DebuggerUtilsAsync.resume(set);
        LOG.debug("Set resumed ");
      }
    };
    pushContext(suspendContext);
    return suspendContext;
  }

  private void pushContext(SuspendContextImpl suspendContext) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    myEventContexts.addFirst(suspendContext);
    suspends++;
    if (LOG.isDebugEnabled()) {
      LOG.debug("Push context : " + suspendContext);
      LOG.debug("Suspends = " + suspends);
    }

    if (DebuggerUtils.isAlwaysSuspendThreadBeforeSwitch()) {
      List<SuspendContextImpl> suspendAllContexts = ContainerUtil.filter(myEventContexts, c -> c.getSuspendPolicy() == EventRequest.SUSPEND_ALL);
      if (suspendAllContexts.size() > 1) {
        logError("More than one suspend all context: " + suspendAllContexts);
      }
    }
  }

  private void logError(String message) {
    myDebugProcess.logError(message);
  }

  @Override
  public void resume(@NotNull SuspendContextImpl context) {
    if (DebuggerUtils.isNewThreadSuspendStateTracking()) {
      resumeNew(context);
    }
    else {
      resumeOld(context);
    }
  }

  private void resumeOld(@NotNull SuspendContextImpl context) {
    SuspendManagerUtil.prepareForResume(context);

    popContext(context);
    context.resume(true);
    myDebugProcess.clearCashes(context);
  }


  private void resumeNew(@NotNull SuspendContextImpl context) {
    ThreadReferenceProxyImpl eventThread = context.getEventThread();
    if (eventThread != null && isFrozen(eventThread)) {
      if (context.getSuspendPolicy() != EventRequest.SUSPEND_EVENT_THREAD) {
        logError("Suspend policy for frozen context " + context + " is " + context.getSuspendPolicy());
      }
      myFrozenThreads.remove(eventThread);
    }

    popContext(context);
    if (context.getSuspendPolicy() == EventRequest.SUSPEND_ALL) {
      if (!ContainerUtil.exists(myPausedContexts, c -> c.getSuspendPolicy() == EventRequest.SUSPEND_ALL)) {
        myExplicitlyResumedThreads.clear();
      }
    }
    Set<ThreadReferenceProxyImpl> resumedThreads = context.myResumedThreads;
    if (resumedThreads != null) {
      for (ThreadReferenceProxyImpl thread : resumedThreads) {
        thread.suspend();
      }
    }
    context.resume(true);
    myDebugProcess.clearCashes(context);
  }

  @Override
  public void popFrame(@NotNull SuspendContextImpl suspendContext) {
    boolean paused = hasPausedContext(suspendContext);
    popContext(suspendContext);
    suspendContext.resume(false); // just set resumed flag for correct commands cancellation
    SuspendContextImpl newSuspendContext = pushSuspendContext(suspendContext.getSuspendPolicy(), 0);
    newSuspendContext.setThread(suspendContext.getEventThread().getThreadReference());
    notifyPaused(newSuspendContext, paused);
  }

  @Override
  public SuspendContextImpl getPausedContext() {
    return myPausedContexts.peekFirst();
  }

  private void popContext(SuspendContextImpl suspendContext) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    suspends--;
    if (LOG.isDebugEnabled()) {
      LOG.debug("popContext, suspends = " + suspends);
    }
    myEventContexts.remove(suspendContext);
    myPausedContexts.remove(suspendContext);
  }

  private void pushPausedContext(SuspendContextImpl suspendContext) {
    if (!myEventContexts.contains(suspendContext)) {
      logError("Suspend context " + suspendContext + " was not in myEventContexts");
    }

    myPausedContexts.addFirst(suspendContext);
  }

  @Override
  public List<SuspendContextImpl> getEventContexts() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    return new ArrayList<>(myEventContexts);
  }

  @Override
  public boolean isFrozen(@NotNull ThreadReferenceProxyImpl thread) {
    return myFrozenThreads.contains(thread);
  }

  @Override
  public boolean isSuspended(@NotNull ThreadReferenceProxyImpl thread) throws ObjectCollectedException {
    DebuggerManagerThreadImpl.assertIsManagerThread();

    boolean suspended;

    if (isFrozen(thread)) {
      suspended = true;
    }
    else {
      suspended = ContainerUtil.exists(myEventContexts, suspendContext -> suspendContext.suspends(thread));
    }

    //bug in JDI : newly created thread may be resumed even when suspendPolicy == SUSPEND_ALL
    //if (LOG.isDebugEnabled() && suspended) {
    //  LOG.assertTrue(thread.suspends(), thread.name());
    //}
    return suspended && thread.isSuspended();
  }

  @Override
  public void suspendThread(@NotNull SuspendContextImpl context, @NotNull ThreadReferenceProxyImpl thread) {
    if (thread == context.getEventThread()) {
      logError("Thread " + thread + " is already suspended at the breakpoint for " + context);
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("Thread " + thread + " is going to be suspended in " + context);
    }

    if (context.isExplicitlyResumed(thread)) {
      context.myResumedThreads.remove(thread);
      context.myNotExecutableThreads.remove(thread);
      performIfNoNewInvocationWatcherTrackThisContext(context, () -> thread.suspend());
    }
    else {
      logError("Thread " + thread + " is trying to be suspended in " + context + " but it is not in explicitly resumed threads");
    }
  }

  @Override
  public void resumeThread(@NotNull SuspendContextImpl context, @NotNull ThreadReferenceProxyImpl thread) {
    if (context.isExplicitlyResumed(thread)) {
      logError("Thread " + thread + " was in explicitly resumed threads for " + context);
    }

    if (context.getSuspendPolicy() == EventRequest.SUSPEND_EVENT_THREAD && context.getEventThread() != thread) {
      logError("Suspend-thread context " + context + " should not resume another thread " + thread);
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("Thread " + thread + " is going to be resumed in " + context);
    }

    if (context.myResumedThreads == null) {
      context.myResumedThreads = ConcurrentCollectionFactory.createConcurrentSet();
    }
    context.myResumedThreads.add(thread);
    context.myNotExecutableThreads.remove(thread);
    performIfNoNewInvocationWatcherTrackThisContext(context, () -> thread.resume());
  }

  private void performIfNoNewInvocationWatcherTrackThisContext(@NotNull SuspendContextImpl context, @NotNull Runnable runnable) {
    ThreadBlockedMonitor.InvocationWatcherNewImpl watching = myDebugProcess.myThreadBlockedMonitor.myInvocationWatching;
    if (watching != null && watching.mySuspendAllContext == context) {
      // Now there is a long invocation in progress, so do not perform actual operations.
      // The watcher will block all when the invocation finished.
      if (LOG.isDebugEnabled()) {
        LOG.debug("No actual thread suspending/resuming happens in " + context + " because of suspend all invocation is going");
      }
    }
    else {
      runnable.run();
    }
  }

  @Override
  public void freezeThread(@NotNull ThreadReferenceProxyImpl thread) {
    if (myFrozenThreads.add(thread)) {
      thread.suspend();
    }
  }

  @Override
  public void unfreezeThread(@NotNull ThreadReferenceProxyImpl thread) {
    if (myFrozenThreads.remove(thread)) {
      thread.resume();
    }
  }

  private void processVote(@NotNull SuspendContextImpl suspendContext) {
    if (suspendContext.myVotesToVote <= 0) {
      logError("Vote counter should be positive for " + suspendContext);
    }
    suspendContext.myVotesToVote--;

    if (LOG.isDebugEnabled()) {
      LOG.debug("myVotesToVote = " + suspendContext.myVotesToVote + " in " + suspendContext);
    }
    if (suspendContext.myVotesToVote == 0) {
      if (suspendContext.myIsVotedForResume) {
        scheduleResume(suspendContext);
      }
      else {
        LOG.debug("vote paused");
        myDebugProcess.cancelRunToCursorBreakpoint();
        if (!Registry.is("debugger.keep.step.requests")) {
          ThreadReferenceProxyImpl thread = suspendContext.getEventThread();
          myDebugProcess.deleteStepRequests(suspendContext.getVirtualMachineProxy().eventRequestManager(),
                                            thread != null ? thread.getThreadReference() : null);
        }

        boolean needSwitchToSuspendAll = false;
        if (DebuggerUtils.isAlwaysSuspendThreadBeforeSwitch() && suspendContext.getSuspendPolicy() == EventRequest.SUSPEND_EVENT_THREAD) {
          EventSet eventSet = suspendContext.getEventSet();
          if (eventSet != null) {
            needSwitchToSuspendAll = RequestManagerImpl.hasSuspendAllRequestor(eventSet);
          }
        }

        boolean isSimplePause = !needSwitchToSuspendAll || !DebugProcessEvents.specialSuspendProcessingForAlwaysSwitch(
          suspendContext, this, Objects.requireNonNull(suspendContext.getEventThread()).getThreadReference()
        );
        if (isSimplePause) {
          notifyPaused(suspendContext, true);
        }
      }
    }
  }

  void scheduleResume(@NotNull SuspendContextImpl suspendContext) {
    if (suspendContext.myVotesToVote != 0) {
      logError("Explicit resuming with remain votes: " + suspendContext.myVotesToVote);
    }
    // resume in a separate request to allow other requests be processed (e.g. dependent bpts enable)
    suspendContext.myIsGoingToResume = true;
    myDebugProcess.getManagerThread().schedule(PrioritizedTask.Priority.HIGH, () -> resume(suspendContext));
  }

  private void notifyPaused(@NotNull SuspendContextImpl suspendContext, boolean pushPaused) {
    if (pushPaused) {
      pushPausedContext(suspendContext);
    }
    myDebugProcess.myDebugProcessListeners.forEach(it -> it.paused(suspendContext));
  }

  @Override
  public void voteResume(@NotNull SuspendContextImpl suspendContext) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Resume voted for " + suspendContext);
    }
    processVote(suspendContext);
  }

  @Override
  public void voteSuspend(@NotNull SuspendContextImpl suspendContext) {
    suspendContext.myIsVotedForResume = false;
    processVote(suspendContext);
  }

  @Override
  public @NotNull List<SuspendContextImpl> getPausedContexts() {
    return new ArrayList<>(myPausedContexts);
  }

  public boolean hasPausedContext(SuspendContextImpl suspendContext) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    return myPausedContexts.contains(suspendContext);
  }

  String getStateForDiagnostics() {
    return "myEventContexts=" + myEventContexts + "\n" +
           "myPausedContexts=" + myPausedContexts + "\n" +
           "myFrozenThreads=" + myFrozenThreads + "\n" +
           "myExplicitlyResumedThreads=" + myExplicitlyResumedThreads + "\n" +
           "suspends=" + suspends + "\n";
  }

  DebugProcessImpl getDebugProcess() {
    return myDebugProcess;
  }
}
