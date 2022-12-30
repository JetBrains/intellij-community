// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine;

import com.intellij.Patches;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.impl.DebuggerUtilsAsync;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.impl.PrioritizedTask;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.sun.jdi.Location;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.LocatableEvent;
import com.sun.jdi.request.EventRequest;
import one.util.streamex.StreamEx;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author lex
 */
public abstract class SuspendContextImpl extends XSuspendContext implements SuspendContext {
  private static final Logger LOG = Logger.getInstance(SuspendContextImpl.class);

  private final DebugProcessImpl myDebugProcess;
  private final int mySuspendPolicy;

  private ThreadReferenceProxyImpl myThread;
  boolean myIsVotedForResume = true;

  protected int myVotesToVote;
  protected Set<ThreadReferenceProxyImpl> myResumedThreads;

  private final EventSet myEventSet;
  private volatile boolean myIsResumed;

  private final ConcurrentLinkedQueue<SuspendContextCommandImpl> myPostponedCommands = new ConcurrentLinkedQueue<>();
  public volatile boolean myInProgress;
  private final HashSet<ObjectReference> myKeptReferences = new HashSet<>();
  private EvaluationContextImpl myEvaluationContext = null;
  private int myFrameCount = -1;

  private JavaExecutionStack myActiveExecutionStack;

  SuspendContextImpl(@NotNull DebugProcessImpl debugProcess,
                     @MagicConstant(flagsFromClass = EventRequest.class) int suspendPolicy,
                     int eventVotes,
                     EventSet set) {
    myDebugProcess = debugProcess;
    mySuspendPolicy = suspendPolicy;
    myVotesToVote = eventVotes;
    myEventSet = set;
  }

  public void setThread(ThreadReference thread) {
    assertNotResumed();
    ThreadReferenceProxyImpl threadProxy = myDebugProcess.getVirtualMachineProxy().getThreadReferenceProxy(thread);
    LOG.assertTrue(myThread == null || myThread == threadProxy);
    myThread = threadProxy;
  }

  public int frameCount() {
    if (myFrameCount == -1) {
      try {
        myFrameCount = myThread != null ? myThread.frameCount() : 0;
      }
      catch (EvaluateException e) {
        myFrameCount = 0;
      }
    }
    return myFrameCount;
  }

  @Nullable
  public Location getLocation() {
    // getting location from the event set is much faster than obtaining the frame and getting it from there
    if (myEventSet != null) {
      LocatableEvent event = StreamEx.of(myEventSet).select(LocatableEvent.class).findFirst().orElse(null);
      if (event != null) {
        if (myThread != null && !myThread.getThreadReference().equals(event.thread())) {
          LOG.error("Invalid thread");
        }
        return event.location();
      }
    }
    try {
      StackFrameProxyImpl frameProxy = getFrameProxy();
      return frameProxy != null ? frameProxy.location() : null;
    }
    catch (Throwable e) {
      LOG.debug(e);
    }
    return null;
  }

  protected abstract void resumeImpl();

  void resume(boolean callResume) {
    assertNotResumed();
    if (isEvaluating()) {
      LOG.error("Resuming context while evaluating", ThreadDumper.dumpThreadsToString());
    }
    DebuggerManagerThreadImpl.assertIsManagerThread();
    try {
      if (!Patches.IBM_JDK_DISABLE_COLLECTION_BUG) {
        // delay enable collection to speedup the resume
        for (ObjectReference r : myKeptReferences) {
          myDebugProcess.getManagerThread().schedule(PrioritizedTask.Priority.LOWEST, () -> DebuggerUtilsEx.enableCollection(r));
        }
        myKeptReferences.clear();
      }

      for(SuspendContextCommandImpl cmd = pollPostponedCommand(); cmd != null; cmd = pollPostponedCommand()) {
        cmd.notifyCancelled();
      }
      if (callResume) {
        resumeImpl();
      }
    }
    finally {
      myIsResumed = true;
    }
  }

  private void assertNotResumed() {
    if (myIsResumed) {
      if (myDebugProcess.isAttached()) {
        LOG.error("Cannot access SuspendContext. SuspendContext is resumed.");
      }
    }
  }


  @Nullable
  public EventSet getEventSet() {
    return myEventSet;
  }

  @Override
  @NotNull
  public DebugProcessImpl getDebugProcess() {
    return myDebugProcess;
  }

  @Override
  public StackFrameProxyImpl getFrameProxy() {
    assertNotResumed();
    try {
      if (myThread != null) {
        int frameCount = myThread.frameCount();
        if (myFrameCount != -1 && myFrameCount != frameCount) {
          LOG.error("Incorrect frame count, cached " + myFrameCount + ", now " + frameCount +
                    ", thread " + myThread + " suspend count " + myThread.getSuspendCount());
        }
        myFrameCount = frameCount;
        if (frameCount > 0) {
          return myThread.frame(0);
        }
      }
      return null;
    }
    catch (EvaluateException ignored) {
      return null;
    }
  }

  @Nullable
  @Override
  public ThreadReferenceProxyImpl getThread() {
    return myThread;
  }

  @MagicConstant(flagsFromClass = EventRequest.class)
  @Override
  public int getSuspendPolicy() {
    return mySuspendPolicy;
  }

  public void doNotResumeHack() {
    assertNotResumed();
    myVotesToVote = 1000000000;
  }

  public boolean isExplicitlyResumed(@Nullable ThreadReferenceProxyImpl thread) {
    return myResumedThreads != null && myResumedThreads.contains(thread);
  }

  public boolean suspends(ThreadReferenceProxyImpl thread) {
    assertNotResumed();
    if(isEvaluating()) {
      return false;
    }
    return switch (getSuspendPolicy()) {
      case EventRequest.SUSPEND_ALL -> !isExplicitlyResumed(thread);
      case EventRequest.SUSPEND_EVENT_THREAD -> thread == getThread();
      default -> false;
    };
  }

  public boolean isEvaluating() {
    assertNotResumed();
    return myEvaluationContext != null;
  }

  public EvaluationContextImpl getEvaluationContext() {
    return myEvaluationContext;
  }

  public boolean isResumed() {
    return myIsResumed;
  }

  public void setIsEvaluating(EvaluationContextImpl evaluationContext) {
    assertNotResumed();
    myEvaluationContext = evaluationContext;
  }

  public String toString() {
    if (myEventSet != null) {
      return myEventSet.toString();
    }
    return myThread != null ? myThread.toString() : JavaDebuggerBundle.message("string.null.context");
  }

  public void keep(ObjectReference reference) {
    if (!Patches.IBM_JDK_DISABLE_COLLECTION_BUG) {
      final boolean added = myKeptReferences.add(reference);
      if (added) {
        DebuggerUtilsEx.disableCollection(reference);
      }
    }
  }

  public final void postponeCommand(final SuspendContextCommandImpl command) {
    if (!isResumed()) {
      // Important! when postponing increment the holds counter, so that the action is not released too early.
      // This will ensure that the counter becomes zero only when the command is actually executed or canceled
      command.hold();
      myPostponedCommands.add(command);
    }
    else {
      command.notifyCancelled();
    }
  }

  public final SuspendContextCommandImpl pollPostponedCommand() {
    return myPostponedCommands.poll();
  }

  @Nullable
  @Override
  public JavaExecutionStack getActiveExecutionStack() {
    return myActiveExecutionStack;
  }

  public void initExecutionStacks(ThreadReferenceProxyImpl activeThread) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if (myThread == null) {
      myThread = activeThread;
    }
    if (activeThread != null) {
      myActiveExecutionStack = new JavaExecutionStack(activeThread, myDebugProcess, myThread == activeThread);
      myActiveExecutionStack.initTopFrame();
    }
  }

  @Override
  public void computeExecutionStacks(final XExecutionStackContainer container) {
    myDebugProcess.getManagerThread().schedule(new SuspendContextCommandImpl(this) {
      final Set<ThreadReferenceProxyImpl> myAddedThreads = new HashSet<>();

      @Override
      public void contextAction(@NotNull SuspendContextImpl suspendContext) {
        List<ThreadReferenceProxyImpl> pausedThreads =
          StreamEx.of(((SuspendManagerImpl)myDebugProcess.getSuspendManager()).getPausedContexts())
            .map(SuspendContextImpl::getThread)
            .nonNull()
            .toList();
        // add paused threads first
        CompletableFuture.completedFuture(pausedThreads)
          .thenCompose(tds -> addThreads(tds, THREAD_NAME_COMPARATOR, false))
          .thenCompose(res -> res
                 ? getDebugProcess().getVirtualMachineProxy().allThreadsAsync()
                 : CompletableFuture.completedFuture(Collections.emptyList()))
          .thenAccept(tds -> addThreads(tds, THREADS_SUSPEND_AND_NAME_COMPARATOR, true))
          .exceptionally(throwable -> DebuggerUtilsAsync.logError(throwable));
      }

      CompletableFuture<Boolean> addThreads(Collection<ThreadReferenceProxyImpl> threads, @Nullable Comparator<? super JavaExecutionStack> comparator, boolean last) {
        List<CompletableFuture<JavaExecutionStack>> futures = new ArrayList<>();
        for (ThreadReferenceProxyImpl thread : threads) {
          if (container.isObsolete()) {
            return CompletableFuture.completedFuture(false);
          }
          if (thread != null && myAddedThreads.add(thread)) {
            futures.add(JavaExecutionStack.create(thread, myDebugProcess, thread == myThread));
          }
        }
        return DebuggerUtilsAsync.reschedule(CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))).thenApply(__ -> {
          if (!container.isObsolete()) {
            StreamEx<JavaExecutionStack> stacks = StreamEx.of(futures).map(CompletableFuture::join);
            if (comparator != null) {
              stacks = stacks.sorted(comparator);
            }
            container.addExecutionStack(stacks.toList(), last);
          }
          return true;
        });
      }
    });
  }

  private static final Comparator<JavaExecutionStack> THREAD_NAME_COMPARATOR =
    Comparator.comparing(XExecutionStack::getDisplayName, String.CASE_INSENSITIVE_ORDER);

  private static final Comparator<ThreadReferenceProxyImpl> SUSPEND_FIRST_COMPARATOR =
    Comparator.comparing(ThreadReferenceProxyImpl::isSuspended).reversed();

  private static final Comparator<JavaExecutionStack> THREADS_SUSPEND_AND_NAME_COMPARATOR =
    Comparator.comparing(JavaExecutionStack::getThreadProxy, SUSPEND_FIRST_COMPARATOR).thenComparing(THREAD_NAME_COMPARATOR);
}
