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

import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.ui.breakpoints.StepIntoBreakpoint;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMethod;
import com.intellij.util.containers.ContainerUtil;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import com.sun.jdi.event.LocatableEvent;
import org.jetbrains.annotations.NotNull;

/**
 * @author egor
 */
public class LambdaAsyncMethodFilter extends BasicStepMethodFilter {
  private final int myParamNo;
  private final LambdaMethodFilter myMethodFilter;

  public LambdaAsyncMethodFilter(@NotNull PsiMethod callerMethod, int paramNo, LambdaMethodFilter methodFilter) {
    super(callerMethod, methodFilter.getCallingExpressionLines());
    myParamNo = paramNo;
    myMethodFilter = methodFilter;
  }

  @Override
  public int onReached(SuspendContextImpl context, RequestHint hint) {
    try {
      StackFrameProxyImpl proxy = context.getFrameProxy();
      if (proxy != null) {
        Value lambdaReference = ContainerUtil.getOrElse(proxy.getArgumentValues(), myParamNo, null);
        if (lambdaReference instanceof ObjectReference) {
          final SourcePosition pos = myMethodFilter.getBreakpointPosition();
          if (pos != null) {
            Project project = context.getDebugProcess().getProject();
            long lambdaId = ((ObjectReference)lambdaReference).uniqueID();
            StepIntoBreakpoint breakpoint = new LambdaInstanceBreakpoint(project, lambdaId, pos, myMethodFilter);
            ClassInstanceMethodFilter.setUpStepIntoBreakpoint(context, breakpoint, hint);
            return RequestHint.RESUME;
          }
        }
      }
    }
    catch (EvaluateException ignore) {
    }
    return RequestHint.STOP;
  }

  private static class LambdaInstanceBreakpoint extends StepIntoBreakpoint {
    private final long myLambdaId;

    public LambdaInstanceBreakpoint(@NotNull Project project,
                                    long lambdaId,
                                    @NotNull SourcePosition pos,
                                    @NotNull BreakpointStepMethodFilter filter) {
      super(project, pos, filter);
      myLambdaId = lambdaId;
    }

    @Override
    public boolean evaluateCondition(EvaluationContextImpl context, LocatableEvent event) throws EvaluateException {
      if (!super.evaluateCondition(context, event)) {
        return false;
      }

      if (!DebuggerUtilsEx.isLambda(event.location().method())) {
        return false;
      }

      // lambda reference is available in the parent frame
      ObjectReference lambdaReference = null;
      StackFrameProxyImpl parentFrame = context.getSuspendContext().getThread().frame(1);
      if (parentFrame != null) {
        try {
          lambdaReference = parentFrame.thisObject();
        }
        catch (EvaluateException ignore) {
        }
      }
      return lambdaReference != null && lambdaReference.uniqueID() == myLambdaId;
    }
  }
}
