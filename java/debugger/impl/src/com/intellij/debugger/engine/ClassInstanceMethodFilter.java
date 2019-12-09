// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.ui.breakpoints.StepIntoBreakpoint;
import com.intellij.psi.PsiMethod;
import com.intellij.util.Range;
import com.sun.jdi.ObjectReference;

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
            DebugProcessImpl.prepareAndSetSteppingBreakpoint(context, breakpoint, hint, true);
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
