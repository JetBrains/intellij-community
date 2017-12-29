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

import com.intellij.debugger.EvaluatingComputable;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.Range;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 */
public class BasicStepMethodFilter implements NamedMethodFilter {
  private static final Logger LOG = Logger.getInstance(BasicStepMethodFilter.class);

  @NotNull
  protected final JVMName myDeclaringClassName;
  @NotNull
  private final String myTargetMethodName;
  @Nullable
  protected final JVMName myTargetMethodSignature;
  private final Range<Integer> myCallingExpressionLines;

  public BasicStepMethodFilter(@NotNull PsiMethod psiMethod, Range<Integer> callingExpressionLines) {
    this(JVMNameUtil.getJVMQualifiedName(psiMethod.getContainingClass()),
         JVMNameUtil.getJVMMethodName(psiMethod),
         JVMNameUtil.getJVMSignature(psiMethod),
         callingExpressionLines);
  }

  protected BasicStepMethodFilter(@NotNull JVMName declaringClassName,
                                  @NotNull String targetMethodName,
                                  @Nullable JVMName targetMethodSignature,
                                  Range<Integer> callingExpressionLines) {
    myDeclaringClassName = declaringClassName;
    myTargetMethodName = targetMethodName;
    myTargetMethodSignature = targetMethodSignature;
    myCallingExpressionLines = callingExpressionLines;
  }

  @NotNull
  public String getMethodName() {
    return myTargetMethodName;
  }

  public boolean locationMatches(DebugProcessImpl process, Location location) throws EvaluateException {
    return locationMatches(process, location, () -> null);
  }

  public boolean locationMatches(DebugProcessImpl process, Location location, @NotNull EvaluatingComputable<ObjectReference> thisProvider)
    throws EvaluateException {
    Method method = location.method();
    String name = method.name();
    if (!myTargetMethodName.equals(name)) {
      if (DebuggerUtilsEx.isLambdaName(name)) {
        SourcePosition position = process.getPositionManager().getSourcePosition(location);
        return ReadAction.compute(() -> {
          PsiElement psiMethod = DebuggerUtilsEx.getContainingMethod(position);
          if (psiMethod instanceof PsiLambdaExpression) {
            PsiType type = ((PsiLambdaExpression)psiMethod).getFunctionalInterfaceType();
            PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(type);
            if (type != null && interfaceMethod != null && myTargetMethodName.equals(interfaceMethod.getName())) {
              try {
                return InheritanceUtil.isInheritor(type, myDeclaringClassName.getName(process).replace('$', '.'));
              }
              catch (EvaluateException e) {
                LOG.info(e);
              }
            }
          }
          return false;
        });
      }
      return false;
    }
    if (myTargetMethodSignature != null && !signatureMatches(method, myTargetMethodSignature.getName(process))) {
      return false;
    }
    if (method.isBridge()) { // skip bridge methods
      return false;
    }
    String declaringClassNameName = myDeclaringClassName.getName(process);
    boolean res = DebuggerUtilsEx.isAssignableFrom(declaringClassNameName, location.declaringType());
    if (!res && !method.isStatic()) {
      ObjectReference thisObject = thisProvider.compute();
      if (thisObject != null) {
        res = DebuggerUtilsEx.isAssignableFrom(declaringClassNameName, thisObject.referenceType());
      }
    }
    return res;
  }

  private static boolean signatureMatches(Method method, final String expectedSignature) throws EvaluateException {
    if (expectedSignature.equals(method.signature())) {
      return true;
    }
    // check if there are any bridge methods that match
    for (Method candidate : method.declaringType().methodsByName(method.name())) {
      if (candidate != method && candidate.isBridge() && expectedSignature.equals(candidate.signature())) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  @Override
  public Range<Integer> getCallingExpressionLines() {
    return myCallingExpressionLines;
  }
}
