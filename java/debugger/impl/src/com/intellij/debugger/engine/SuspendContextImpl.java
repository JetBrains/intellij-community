// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine;

import com.intellij.Patches;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.engine.requests.RequestManagerImpl;
import com.intellij.debugger.impl.DebuggerUtilsAsync;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.impl.PrioritizedTask;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.CheckedDisposable;
import com.intellij.openapi.util.Disposer;
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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

public abstract class SuspendContextImpl extends XSuspendContext implements SuspendContext, Disposable {
  private static final Logger LOG = Logger.getInstance(SuspendContextImpl.class);

  private final long myDebugId;

  private final DebugProcessImpl myDebugProcess;
  private final int mySuspendPolicy;
  private final VirtualMachineProxyImpl myVirtualMachine;

  private ThreadReferenceProxyImpl myThread;
  boolean myIsVotedForResume = true;

  protected int myVotesToVote;
  protected Set<ThreadReferenceProxyImpl> myResumedThreads;

  protected final Set<ThreadReferenceProxyImpl> myNotExecutableThreads = new HashSet<>();

  // There may be several events for the same break-point. So let's use custom processing if any of them is wanted it.
  protected boolean myIsCustomSuspendLogic = false;
  protected boolean mySuspendAllSwitchedContext = false;

  private EventSet myEventSet;
  private volatile boolean myIsResumed;
  protected volatile boolean myIsGoingToResume;

  private final ConcurrentLinkedQueue<SuspendContextCommandImpl> myPostponedCommands = new ConcurrentLinkedQueue<>();
  public volatile boolean myInProgress;
  private final HashSet<ObjectReference> myKeptReferences = new HashSet<>();
  private EvaluationContextImpl myEvaluationContext = null;
  private int myFrameCount = -1;

  private JavaExecutionStack myActiveExecutionStack;

  private final ThreadReferenceProxyImpl.ThreadListener myListener = new ThreadReferenceProxyImpl.ThreadListener() {
    @Override
    public void threadSuspended() {
      myNotExecutableThreads.clear();
      myFrameCount = -1;
    }

    @Override
    public void threadResumed() {
      myNotExecutableThreads.clear();
      myFrameCount = -1;
    }
  };

  SuspendContextImpl(@NotNull DebugProcessImpl debugProcess,
                     @MagicConstant(flagsFromClass = EventRequest.class) int suspendPolicy,
                     int eventVotes,
                     @Nullable EventSet set,
                     long debugId) {
    myDebugProcess = debugProcess;
    mySuspendPolicy = suspendPolicy;
    // Save the VM related to this suspend context, as a VM may be changed due to reattach
    myVirtualMachine = debugProcess.getVirtualMachineProxy();
    myVotesToVote = eventVotes;
    myEventSet = set;
    myDebugId = debugId;
    CheckedDisposable disposable = debugProcess.disposable;
    if (disposable.isDisposed()) {
      // could be due to VM death
      Disposer.dispose(this);
    }
    else {
      Disposer.register(disposable, this);
    }
  }

  public VirtualMachineProxyImpl getVirtualMachineProxy() {
    return myVirtualMachine;
  }

  protected void setEventSet(EventSet eventSet) {
    assertCanBeUsed();
    assertInLog(myEventSet == null, () -> "Event set in " + this + "should be empty");
    myEventSet = eventSet;
  }

  public void setThread(@Nullable ThreadReference thread) {
    assertCanBeUsed();
    ThreadReferenceProxyImpl threadProxy = myVirtualMachine.getThreadReferenceProxy(thread);
    assertInLog(myThread == null || myThread == threadProxy,
                () -> "Invalid thread setting in " + this + ": myThread = " + myThread + ", thread = " + thread);
    setThread(threadProxy);
  }

  void resetThread(@NotNull ThreadReferenceProxyImpl threadProxy) {
    if (myThread == threadProxy) {
      return;
    }
    assertInLog(myEvaluationContext == null, () -> "Resetting thread during evaluation is not supported: " + this);
    assertInLog(myActiveExecutionStack == null, () -> "Thread should be retested before the active execution stack initialization: " + this);
    assertCanBeUsed();
    if (myThread != null) {
      myThread.removeListener(myListener);
    }
    myFrameCount = -1;
    setThread(threadProxy);
  }

  private void setThread(@Nullable ThreadReferenceProxyImpl threadProxy) {
    if (threadProxy != null && myThread != threadProxy && !myDebugProcess.disposable.isDisposed()) { // do not add more than once
      threadProxy.addListener(myListener, this);
    }
    myThread = threadProxy;
  }

  @Override
  public void dispose() {
  }

  int getCachedThreadFrameCount() {
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
    if ((myActiveExecutionStack == null || myActiveExecutionStack.getThreadProxy() == myThread) && myEventSet != null) {
      LocatableEvent event = StreamEx.of(myEventSet).select(LocatableEvent.class).findFirst().orElse(null);
      if (event != null) {
        // myThread can be reset to the different thread in resetThread() method
        if (myThread == null || myThread.getThreadReference().equals(event.thread())) {
          return event.location();
        }
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
      logError("Resuming context " + this + " while evaluating");
    }
    DebuggerManagerThreadImpl.assertIsManagerThread();
    try {
      if (!Patches.IBM_JDK_DISABLE_COLLECTION_BUG) {
        // delay enable collection to speed up the resume
        for (ObjectReference r : myKeptReferences) {
          myDebugProcess.getManagerThread().schedule(PrioritizedTask.Priority.LOWEST, () -> DebuggerUtilsEx.enableCollection(r));
        }
        myKeptReferences.clear();
      }

      cancelAllPostponed();
      if (callResume) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Resuming " + this);
        }
        resumeImpl();
      }
    }
    finally {
      myIsResumed = true;
      Disposer.dispose(this);
    }
  }

  private void assertCanBeUsed() {
    assertNotResumed();
    assertInLog(!myIsGoingToResume, () -> "Context " + this + " is going to resume.");
  }

  private void assertNotResumed() {
    if (myIsResumed) {
      if (myDebugProcess.isAttached()) {
        logError("Cannot access " + this + " because it is resumed.");
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
  public @Nullable StackFrameProxyImpl getFrameProxy() {
    if (myActiveExecutionStack != null) {
      try {
        return myActiveExecutionStack.getThreadProxy().frame(0);
      }
      catch (EvaluateException e) {
        myDebugProcess.logError("Error in proxy extracting", e);
      }
    }
    return getFrameProxyFromTechnicalThread();
  }

  private @Nullable StackFrameProxyImpl getFrameProxyFromTechnicalThread() {
    assertNotResumed();
    try {
      if (myThread != null) {
        int frameCount = myThread.frameCount();
        if (myFrameCount != -1 && myFrameCount != frameCount) {
          logError("Incorrect frame count, cached " + myFrameCount + ", now " + frameCount +
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
    if (myActiveExecutionStack != null) {
      return myActiveExecutionStack.getThreadProxy();
    }
    return myThread;
  }

  /** The thead that comes from the JVM event or was reset by switching to suspend-all procedure */
  @ApiStatus.Internal
  public @Nullable ThreadReferenceProxyImpl getEventThread() {
    return myThread;
  }

  @MagicConstant(flagsFromClass = EventRequest.class)
  @Override
  public int getSuspendPolicy() {
    return mySuspendPolicy;
  }

  public String getSuspendPolicyFromRequestors() {
    if (mySuspendPolicy == EventRequest.SUSPEND_ALL) {
      return DebuggerSettings.SUSPEND_ALL;
    }
    if (myEventSet != null) {
      return RequestManagerImpl.hasSuspendAllRequestor(myEventSet) ? DebuggerSettings.SUSPEND_ALL : asStrPolicy();
    }

    return asStrPolicy();
  }

  private String asStrPolicy() {
    return switch (mySuspendPolicy) {
      case EventRequest.SUSPEND_ALL -> DebuggerSettings.SUSPEND_ALL;
      case EventRequest.SUSPEND_EVENT_THREAD -> DebuggerSettings.SUSPEND_THREAD;
      case EventRequest.SUSPEND_NONE -> DebuggerSettings.SUSPEND_NONE;
      default -> throw new IllegalStateException("Cannot convert number " + mySuspendPolicy);
    };
  }


  @SuppressWarnings("unused")
  public void doNotResumeHack() {
    assertNotResumed();
    myVotesToVote = 1000000000;
  }

  public boolean isExplicitlyResumed(@NotNull ThreadReferenceProxyImpl thread) {
    return myResumedThreads != null && myResumedThreads.contains(thread);
  }

  public boolean suspends(@NotNull ThreadReferenceProxyImpl thread) {
    assertNotResumed();
    if (myEvaluationContext != null && thread == myEvaluationContext.getThreadForEvaluation()) {
      return false;
    }
    return switch (getSuspendPolicy()) {
      case EventRequest.SUSPEND_ALL -> !isExplicitlyResumed(thread);
      case EventRequest.SUSPEND_EVENT_THREAD -> thread == myThread;
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
    return myIsResumed || myIsGoingToResume;
  }

  @ApiStatus.Internal
  public void setIsEvaluating(EvaluationContextImpl evaluationContext) {
    assertCanBeUsed();
    myEvaluationContext = evaluationContext;
  }

  public String toString() {
    return "{" + myDebugId + "} " + "SP=" + getSuspendPolicyString() + " " + oldToString();
  }

  private String eventSetAsString() {
    if (myEventSet == null) {
      return "null";
    }
    if (DebuggerDiagnosticsUtil.needAnonymizedReports()) {
      return "EventSet" + DebuggerDiagnosticsUtil.getEventSetClasses(myEventSet) + " in " + myThread;
    }
    return myEventSet.toString();
  }

  private String getStackStr() {
    if (myActiveExecutionStack == null) {
      return "null";
    }
    return DebuggerDiagnosticsUtil.needAnonymizedReports() ? ("Stack in " + myThread) : myActiveExecutionStack.toString();
  }

  private String oldToString() {
    if (myEventSet != null) {
      return eventSetAsString();
    }
    return myThread != null ? myThread.toString() : JavaDebuggerBundle.message("string.null.context");
  }

  String toAttachmentString() {
    StringBuilder sb = new StringBuilder();
    sb.append("------------------\ncontext ").append(this).append(":\n");
    sb.append("myDebugId = ").append(myDebugId).append("\n");
    sb.append("myThread = ").append(myThread).append("\n");
    sb.append("Suspend policy = ").append(getSuspendPolicyString()).append("\n");
    sb.append("myEventSet = ").append(eventSetAsString()).append("\n");
    sb.append("myInProgress = ").append(myInProgress).append("\n");
    sb.append("myEvaluationContext = ").append(myEvaluationContext).append("\n");
    sb.append("myFrameCount = ").append(myFrameCount).append("\n");
    sb.append("myActiveExecutionStack = ").append(getStackStr()).append("\n");

    if (myResumedThreads != null && !myResumedThreads.isEmpty()) {
      sb.append("myResumedThreads:\n");
      for (ThreadReferenceProxyImpl thread : myResumedThreads) {
        sb.append("  ").append(thread).append("\n");
      }
    }

    if (!myNotExecutableThreads.isEmpty()) {
      sb.append("myNotExecutableThreads:\n");
      for (ThreadReferenceProxyImpl thread : myNotExecutableThreads) {
        sb.append("  ").append(thread).append("\n");
      }
    }

    sb.append("mySuspendAllSwitchedContext = ").append(mySuspendAllSwitchedContext).append("\n");
    sb.append("myIsCustomSuspendLogic = ").append(myIsCustomSuspendLogic).append("\n");
    sb.append("myPostponedCommands: ").append(myPostponedCommands.size()).append("\n");
    sb.append("myKeptReferences: ").append(myKeptReferences.size()).append("\n");
    sb.append("myIsVotedForResume = ").append(myIsVotedForResume).append("\n");
    sb.append("myVotesToVote = ").append(myVotesToVote).append("\n");
    sb.append("myIsResumed = ").append(myIsResumed).append("\n");
    sb.append("myIsGoingToResume = ").append(myIsGoingToResume).append("\n");
    return sb.toString();
  }

  private String getSuspendPolicyString() {
    return switch (getSuspendPolicy()) {
      case EventRequest.SUSPEND_EVENT_THREAD -> "thread";
      case EventRequest.SUSPEND_ALL -> "all";
      case EventRequest.SUSPEND_NONE -> "none";
      default -> "other";
    };
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

  public final void cancelAllPostponed() {
    for (SuspendContextCommandImpl postponed = pollPostponedCommand(); postponed != null; postponed = pollPostponedCommand()) {
      postponed.notifyCancelled();
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
    assertCanBeUsed();
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if (myThread == null) {
      setThread(activeThread);
    }
    if (activeThread != null) {
      myActiveExecutionStack = new JavaExecutionStack(activeThread, myDebugProcess, myThread == activeThread);
      myActiveExecutionStack.initTopFrame();
    }
  }

  @Override
  public void computeExecutionStacks(final XExecutionStackContainer container) {
    assertCanBeUsed();
    myDebugProcess.getManagerThread().schedule(new SuspendContextCommandImpl(this) {
      final Set<ThreadReferenceProxyImpl> myAddedThreads = new HashSet<>();

      @Override
      public void contextAction(@NotNull SuspendContextImpl suspendContext) {
        List<ThreadReferenceProxyImpl> pausedThreads =
          StreamEx.of(myDebugProcess.getSuspendManager().getPausedContexts())
            .map(SuspendContextImpl::getEventThread)
            .nonNull()
            .toList();
        // add paused threads first
        CompletableFuture.completedFuture(pausedThreads)
          .thenCompose(tds -> addThreads(tds, THREAD_NAME_COMPARATOR, false))
          .thenCompose(res -> res
                              ? suspendContext.getVirtualMachineProxy().allThreadsAsync()
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

  private void logError(@NotNull String message) {
    myDebugProcess.logError(message);
  }

  private void assertInLog(boolean value, @NotNull Supplier<@NotNull String> supplier) {
    if (!value) {
      myDebugProcess.logError(supplier.get());
    }
  }
}
