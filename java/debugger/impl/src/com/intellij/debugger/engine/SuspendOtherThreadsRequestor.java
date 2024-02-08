// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine;

import com.intellij.debugger.engine.evaluation.EvaluationListener;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.breakpoints.FilteredRequestor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.SingleAlarm;
import com.sun.jdi.event.LocatableEvent;
import com.sun.jdi.request.EventRequest;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

class SuspendOtherThreadsRequestor implements FilteredRequestor {
  private final @NotNull DebugProcessImpl myProcess;
  private final @NotNull ParametersForSuspendAllReplacing myParameters;

  SuspendOtherThreadsRequestor(@NotNull DebugProcessImpl process, @NotNull ParametersForSuspendAllReplacing parameters) {
    myProcess = process;
    myParameters = parameters;
  }

  static void initiateTransferToSuspendAll(@NotNull DebugProcessImpl process,
                                           @NotNull SuspendContextImpl suspendContext,
                                           @NotNull Function<SuspendContextImpl, Boolean> performOnSuspendAll) {
    process.myPreparingToSuspendAll = true;
    process.myParametersForSuspendAllReplacing = new ParametersForSuspendAllReplacing(suspendContext, performOnSuspendAll);
    EvaluationListener listener = addFinishEvaluationListener(process);
    boolean isSuccessTry = tryToIssueSuspendContextReplacement(process);
    if (isSuccessTry) {
      process.removeEvaluationListener(listener);
    }
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

    myProcess.myPreparingToSuspendAll = false;

    // Need to 'replace' the myThreadSuspendContext (single-thread suspend context passed filtering) with this one.
    suspendContext.setAnotherThreadToFocus(myParameters.getThreadSuspendContext().getThread());

    // Note, myThreadSuspendContext is resuming without SuspendManager#voteSuspend.
    // Look at the end of DebugProcessEvents#processLocatableEvent for more details.
    myProcess.getSuspendManager().voteResume(myParameters.getThreadSuspendContext());

    return myParameters.getPerformOnSuspendAll().apply(suspendContext);
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
