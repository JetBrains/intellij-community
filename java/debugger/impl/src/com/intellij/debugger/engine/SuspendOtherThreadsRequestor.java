// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine;

import com.intellij.debugger.engine.evaluation.EvaluationListener;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.breakpoints.FilteredRequestor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.SingleAlarm;
import com.sun.jdi.event.LocatableEvent;
import com.sun.jdi.request.EventRequest;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.function.Function;

public class SuspendOtherThreadsRequestor implements FilteredRequestor {
  private final @NotNull DebugProcessImpl myProcess;
  private final @NotNull ParametersForSuspendAllReplacing myParameters;

  private SuspendOtherThreadsRequestor(@NotNull DebugProcessImpl process, @NotNull ParametersForSuspendAllReplacing parameters) {
    myProcess = process;
    myParameters = parameters;
  }

  /**
   * Schedule switch-request to move from suspend-thread mode to suspend-all mode.
   *
   * @param performOnSuspendAll The callback is applying on suspend-all context after replacing suspend-thread context.
   * So the callback can schedule additional requests and specify whether the suspend-all event should hold suspension
   * (true) or resume (false) it.
   *
   * @return true iff no need to perform standard resume procedure after the scheduling switch-request was done
   */
  public static boolean initiateTransferToSuspendAll(@NotNull SuspendContextImpl suspendContext,
                                                     @NotNull Function<@NotNull SuspendContextImpl, Boolean> performOnSuspendAll) {
    if (suspendContext.getSuspendPolicy() != EventRequest.SUSPEND_EVENT_THREAD) {
      Logger.getInstance(SuspendOtherThreadsRequestor.class)
        .error("Replacing for all-thread mode can be done only from the suspend-thread mode");
      return false;
    }
    @NotNull DebugProcessImpl process = suspendContext.getDebugProcess();
    if (process.myPreparingToSuspendAll) {
      process.mySuspendAllListeners.add(() -> {
        // Now all threads are stopped. But it would be nice to report about it.
        process.getSuspendManager().voteResume(suspendContext);
        suspendContext.getDebugProcess().notifyStoppedOtherThreads();
      });
      return true;
    }

    process.myPreparingToSuspendAll = true;

    if (Registry.is("debugger.transfer.context.to.suspend.all.with.method.breakpoint")) {
      process.myParametersForSuspendAllReplacing = new ParametersForSuspendAllReplacing(suspendContext, performOnSuspendAll);
      EvaluationListener listener = addFinishEvaluationListener(process);
      boolean isSuccessTry = tryToIssueSuspendContextReplacement(process);
      if (isSuccessTry) {
        process.removeEvaluationListener(listener);
      }
    }
    else {
      suspendWhenNoEvaluation(suspendContext, performOnSuspendAll);
    }
    return true;
  }

  /**
   * We do vm suspend to reduce the need for synchronization:
   * while vm is fully stopped, we may be sure that there will be no changes in evaluation
   * if getNumberOfEvaluations is not 0, we resume, see {@link #switchContextWithSuspend}
   */
  private static void suspendWhenNoEvaluation(@NotNull SuspendContextImpl suspendContext,
                                              @NotNull Function<@NotNull SuspendContextImpl, Boolean> performOnSuspendAll) {
    DebugProcessImpl process = suspendContext.getDebugProcess();
    if (!switchContextWithSuspend(process, suspendContext, performOnSuspendAll)) {
      process.addEvaluationListener(new EvaluationListener() {
        @Override
        public void evaluationFinished(SuspendContextImpl context) {
          if (!process.myPreparingToSuspendAll) {
            process.removeEvaluationListener(this);
            return;
          }
          if (switchContextWithSuspend(process, suspendContext, performOnSuspendAll)) {
            process.removeEvaluationListener(this);
          }
          else {
            process.getVirtualMachineProxy().getVirtualMachine().resume();
          }
        }
      });
      process.getVirtualMachineProxy().getVirtualMachine().resume();
    }
  }

  private static boolean switchContextWithSuspend(@NotNull DebugProcessImpl process,
                                                  @NotNull SuspendContextImpl suspendContext,
                                                  @NotNull Function<@NotNull SuspendContextImpl, Boolean> performOnSuspendAll) {
    process.getVirtualMachineProxy().getVirtualMachine().suspend();
    if (getNumberOfEvaluations(process) == 0) {
      SuspendManager suspendManager = process.getSuspendManager();
      SuspendContextImpl newSuspendContext = suspendManager.pushSuspendContext(EventRequest.SUSPEND_ALL, 1);
      // It is an optimization to reduce the synchronous packets number
      newSuspendContext.setEventSet(suspendContext.getEventSet());
      newSuspendContext.setThread(suspendContext.getEventThread().getThreadReference());
      if (processSuspendAll(newSuspendContext, suspendContext, performOnSuspendAll)) {
        process.getManagerThread().schedule(new SuspendContextCommandImpl(newSuspendContext) {
          @Override
          public void contextAction(@NotNull SuspendContextImpl suspendContext) {
            suspendManager.voteSuspend(newSuspendContext);
          }
        });
      }
      else {
        suspendManager.resume(newSuspendContext);
      }
      return true;
    }
    return false;
  }

  private static void enableRequest(DebugProcessImpl process, @NotNull ParametersForSuspendAllReplacing parameters) {
    var requestor = new SuspendOtherThreadsRequestor(process, parameters);
    var request = process.getRequestsManager().createMethodEntryRequest(requestor);

    request.setSuspendPolicy(EventRequest.SUSPEND_ALL);
    request.setEnabled(true);
  }

  private static @NotNull EvaluationListener addFinishEvaluationListener(@NotNull DebugProcessImpl process) {
    EvaluationListener listener = new EvaluationListener() {
      @Override
      public void evaluationFinished(SuspendContextImpl context) {
        tryToIssueSuspendContextReplacement(process);
        if (process.myParametersForSuspendAllReplacing == null) {
          process.removeEvaluationListener(this);
        }
      }
    };
    process.addEvaluationListener(listener);
    return listener;
  }

  private static boolean tryToIssueSuspendContextReplacement(@NotNull DebugProcessImpl process) {
    if (process.myParametersForSuspendAllReplacing == null) {
      return false;
    }
    long count = getNumberOfEvaluations(process);
    if (count != 0) {
      return false;
    }
    ParametersForSuspendAllReplacing needToResume;
    synchronized (process.myEvaluationStateLock) { // here can be any (same object) lock just to synchronize this place
      needToResume = process.myParametersForSuspendAllReplacing;
      if (needToResume != null) {
        process.myParametersForSuspendAllReplacing = null;
      }
    }
    if (needToResume != null) {
      enableRequest(process, needToResume);
      return true;
    }
    return false;
  }

  private static long getNumberOfEvaluations(DebugProcessImpl process) {
    SuspendManager suspendManager = process.getSuspendManager();
    synchronized (process.myEvaluationStateLock) {
      return suspendManager.getEventContexts().stream().filter(c -> c.isEvaluating()).count();
    }
  }


  @Override
  public boolean processLocatableEvent(@NotNull SuspendContextCommandImpl action, LocatableEvent event) {
    myProcess.getRequestsManager().deleteRequest(this);

    SuspendContextImpl suspendContext = action.getSuspendContext();
    if (suspendContext == null) return false;

    if (getNumberOfEvaluations(myProcess) != 0) {
      // Should be very rage. It means some evaluation was started after we specified to hold new evaluation (at least, for filtering).
      Logger.getInstance(getClass()).warn("Fails attempt to switch from suspend-thread context to suspend-all context. Will be rescheduled.");
      // Reschedule the request after some time to finish the evaluation.
      // noinspection SSBasedInspection
      new SingleAlarm(() -> myProcess.getManagerThread().schedule(new DebuggerCommandImpl() {
        @Override
        protected void action() {
          enableRequest(myProcess, myParameters);
        }
      }), 200).request();
      return false;
    }

    return processSuspendAll(suspendContext, myParameters.getThreadSuspendContext(), myParameters.getPerformOnSuspendAll());
  }

  private static boolean processSuspendAll(@NotNull SuspendContextImpl suspendContext,
                                           @NotNull SuspendContextImpl originalContext,
                                           @NotNull Function<@NotNull SuspendContextImpl, Boolean> performOnSuspendAll) {
    // Need to 'replace' the myThreadSuspendContext (single-thread suspend context passed filtering) with this one.
    suspendContext.resetThread(Objects.requireNonNull(originalContext.getEventThread()));

    // Note, myThreadSuspendContext is resuming without SuspendManager#voteSuspend.
    // Look at the end of DebugProcessEvents#processLocatableEvent for more details.
    suspendContext.getDebugProcess().getSuspendManager().voteResume(originalContext);

    suspendContext.mySuspendAllSwitchedContext = true;
    DebugProcessImpl process = suspendContext.getDebugProcess();
    for (Runnable listener : process.mySuspendAllListeners) {
      listener.run();
    }
    process.mySuspendAllListeners.clear();
    process.myPreparingToSuspendAll = false;

    return performOnSuspendAll.apply(suspendContext);
  }

  @Override
  public String getSuspendPolicy() {
    return DebuggerSettings.SUSPEND_ALL;
  }

  @Override
  public boolean shouldIgnoreThreadFiltering() {
    return true;
  }
}
