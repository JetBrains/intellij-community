/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

/**
 * @author egor
 */
public class ClassInstanceMethodFilter extends ConstructorStepMethodFilter implements ActionMethodFilter {
  private final BreakpointStepMethodFilter myMethodFilter;

  public ClassInstanceMethodFilter(PsiMethod psiMethod, Range<Integer> lines) {
    super(psiMethod.getContainingClass(), lines);
    myMethodFilter = new AnonymousClassMethodFilter(psiMethod, getCallingExpressionLines());
  }

  public ClassInstanceMethodFilter(JVMName classJvmName, BreakpointStepMethodFilter methodFilter, Range<Integer> lines) {
    super(classJvmName, lines);
    myMethodFilter = methodFilter;
  }

  @Override
  public int onReached(SuspendContextImpl context) {
    StackFrameProxyImpl proxy = context.getFrameProxy();
    if (proxy != null) {
      try {
        ObjectReference reference = proxy.thisObject();
        if (reference != null) {
          DebugProcessImpl debugProcess = context.getDebugProcess();
          BreakpointManager breakpointManager = DebuggerManagerEx.getInstanceEx(debugProcess.getProject()).getBreakpointManager();
          StepIntoBreakpoint breakpoint = breakpointManager.addStepIntoBreakpoint(myMethodFilter);
          if (breakpoint != null) {
            breakpointManager.applyThreadFilter(debugProcess, null); // clear the filter on resume
            breakpoint.addInstanceFilter(reference.uniqueID());
            breakpoint.setInstanceFiltersEnabled(true);
            breakpoint.setSuspendPolicy(context.getSuspendPolicy() == EventRequest.SUSPEND_EVENT_THREAD ? DebuggerSettings.SUSPEND_THREAD : DebuggerSettings.SUSPEND_ALL);
            breakpoint.createRequest(debugProcess);
            debugProcess.setRunToCursorBreakpoint(breakpoint);
            return RequestHint.RESUME;
          }
        }
      }
      catch (EvaluateException ignored) {
      }
    }
    return RequestHint.STOP;
  }
}
