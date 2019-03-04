// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.actions;

import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.*;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.util.List;

public abstract class JvmSmartStepIntoHandler {
  public static final ExtensionPointName<JvmSmartStepIntoHandler> EP_NAME = ExtensionPointName.create("com.intellij.debugger.jvmSmartStepIntoHandler");

  @NotNull
  public List<SmartStepTarget> findSmartStepTargets(SourcePosition position) {
    throw new AbstractMethodError();
  }

  @NotNull
  public Promise<List<SmartStepTarget>> findSmartStepTargetsAsync(SourcePosition position, DebuggerSession session) {
    return Promises.resolvedPromise(findSmartStepTargets(position));
  }

  @NotNull
  public Promise<List<SmartStepTarget>> findStepIntoTargets(SourcePosition position, DebuggerSession session) {
    return Promises.rejectedPromise();
  }

  public abstract boolean isAvailable(SourcePosition position);

  /**
   * Override in case if your JVMNames slightly different then it can be provided by getJvmSignature method.
   *
   * @param stepTarget
   * @return SmartStepFilter
   */
  @Nullable
  protected MethodFilter createMethodFilter(SmartStepTarget stepTarget) {
    if (stepTarget instanceof MethodSmartStepTarget) {
      MethodSmartStepTarget methodSmartStepTarget = (MethodSmartStepTarget)stepTarget;
      final PsiMethod method = methodSmartStepTarget.getMethod();
      if (stepTarget.needsBreakpointRequest()) {
        return Registry.is("debugger.async.smart.step.into") && method.getContainingClass() instanceof PsiAnonymousClass
               ? new ClassInstanceMethodFilter(method, stepTarget.getCallingExpressionLines())
               : new AnonymousClassMethodFilter(method, stepTarget.getCallingExpressionLines());
      }
      else {
        return new BasicStepMethodFilter(method, methodSmartStepTarget.getOrdinal(), stepTarget.getCallingExpressionLines());
      }
    }
    if (stepTarget instanceof LambdaSmartStepTarget) {
      LambdaSmartStepTarget lambdaTarget = (LambdaSmartStepTarget)stepTarget;
      LambdaMethodFilter lambdaMethodFilter =
        new LambdaMethodFilter(lambdaTarget.getLambda(), lambdaTarget.getOrdinal(), stepTarget.getCallingExpressionLines());

      if (Registry.is("debugger.async.smart.step.into") && lambdaTarget.isAsync()) {
        PsiLambdaExpression lambda = ((LambdaSmartStepTarget)stepTarget).getLambda();
        PsiElement expressionList = lambda.getParent();
        if (expressionList instanceof PsiExpressionList) {
          PsiElement method = expressionList.getParent();
          if (method instanceof PsiMethodCallExpression) {
            return new LambdaAsyncMethodFilter(((PsiMethodCallExpression)method).resolveMethod(),
                                               LambdaUtil.getLambdaIdx((PsiExpressionList)expressionList, lambda),
                                               lambdaMethodFilter);
          }
        }
      }

      return lambdaMethodFilter;
    }
    return null;
  }
}
