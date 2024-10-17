// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine;

import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.MethodBytecodeUtil;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.ui.breakpoints.StepIntoBreakpoint;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMethod;
import com.intellij.util.containers.ContainerUtil;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import com.sun.jdi.event.LocatableEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LambdaAsyncMethodFilter extends BasicStepMethodFilter {
  private final int myParamNo;
  private final LambdaMethodFilter myMethodFilter;

  public LambdaAsyncMethodFilter(@NotNull PsiMethod callerMethod, int paramNo, LambdaMethodFilter methodFilter) {
    super(callerMethod, methodFilter.getCallingExpressionLines());
    myParamNo = paramNo;
    myMethodFilter = methodFilter;
  }

  @Override
  public boolean locationMatches(DebugProcessImpl process, Location location, @Nullable StackFrameProxyImpl frameProxy) throws EvaluateException {
    if (super.locationMatches(process, location, frameProxy) && frameProxy != null) {
      Value lambdaReference = getLambdaReference(frameProxy);
      if (lambdaReference instanceof ObjectReference) {
        Method lambdaMethod = MethodBytecodeUtil.getLambdaMethod(
          ((ObjectReference)lambdaReference).referenceType(), lambdaReference.virtualMachine()::classesByName);
        Location newLocation = lambdaMethod != null ? ContainerUtil.getFirstItem(DebuggerUtilsEx.allLineLocations(lambdaMethod)) : null;
        return newLocation != null && myMethodFilter.locationMatches(process, newLocation);
      }
    }
    return false;
  }

  @Override
  public int onReached(SuspendContextImpl context, RequestHint hint) {
    try {
      StackFrameProxyImpl proxy = context.getFrameProxy();
      if (proxy != null) {
        Value lambdaReference = getLambdaReference(proxy);
        if (lambdaReference instanceof ObjectReference) {
          final SourcePosition pos = myMethodFilter.getBreakpointPosition();
          if (pos != null) {
            Project project = context.getDebugProcess().getProject();
            long lambdaId = ((ObjectReference)lambdaReference).uniqueID();
            StepIntoBreakpoint breakpoint = new LambdaInstanceBreakpoint(project, lambdaId, pos, myMethodFilter);
            DebugProcessImpl.prepareAndSetSteppingBreakpoint(context, breakpoint, hint, true);
            return RequestHint.RESUME;
          }
        }
      }
    }
    catch (EvaluateException ignore) {
    }
    return RequestHint.STOP;
  }

  @Nullable
  private Value getLambdaReference(StackFrameProxyImpl proxy) throws EvaluateException {
    return ContainerUtil.getOrElse(proxy.getArgumentValues(), myParamNo, null);
  }

  private static class LambdaInstanceBreakpoint extends StepIntoBreakpoint {
    private final long myLambdaId;

    LambdaInstanceBreakpoint(@NotNull Project project,
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
