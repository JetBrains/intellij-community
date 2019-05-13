/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.breakpoints.BreakpointManager;
import com.intellij.debugger.ui.breakpoints.StepIntoBreakpoint;
import com.intellij.psi.PsiMethod;
import com.intellij.util.Range;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.request.EventRequest;
import org.jetbrains.annotations.NotNull;

/**
 * @author egor
 */
public class ClassInstanceMethodFilter extends ConstructorStepMethodFilter {
  private final BreakpointStepMethodFilter myMethodFilter;

  public ClassInstanceMethodFilter(PsiMethod psiMethod, Range<Integer> lines) {
    super(psiMethod.getContainingClass(), lines);
    myMethodFilter = new AnonymousClassMethodFilter(psiMethod, getCallingExpressionLines());
  }

  @Override
  public int onReached(SuspendContextImpl context, RequestHint hint) {
    StackFrameProxyImpl proxy = context.getFrameProxy();
    if (proxy != null) {
      try {
        ObjectReference reference = proxy.thisObject();
        if (reference != null) {
          StepIntoBreakpoint breakpoint =
            DebuggerManagerEx.getInstanceEx(context.getDebugProcess().getProject()).getBreakpointManager().addStepIntoBreakpoint(myMethodFilter);
          if (breakpoint != null) {
            breakpoint.addInstanceFilter(reference.uniqueID());
            breakpoint.setInstanceFiltersEnabled(true);
            setUpStepIntoBreakpoint(context, breakpoint, hint);
            return RequestHint.RESUME;
          }
        }
      }
      catch (EvaluateException ignored) {
      }
    }
    return RequestHint.STOP;
  }

  static void setUpStepIntoBreakpoint(SuspendContextImpl context, @NotNull StepIntoBreakpoint breakpoint, RequestHint hint) {
    DebugProcessImpl debugProcess = context.getDebugProcess();
    BreakpointManager breakpointManager = DebuggerManagerEx.getInstanceEx(debugProcess.getProject()).getBreakpointManager();
    breakpointManager.applyThreadFilter(debugProcess, null); // clear the filter on resume
    breakpoint.setSuspendPolicy(
      context.getSuspendPolicy() == EventRequest.SUSPEND_EVENT_THREAD ? DebuggerSettings.SUSPEND_THREAD : DebuggerSettings.SUSPEND_ALL);
    breakpoint.createRequest(debugProcess);
    breakpoint.setRequestHint(hint);
    debugProcess.setRunToCursorBreakpoint(breakpoint);
  }
}
