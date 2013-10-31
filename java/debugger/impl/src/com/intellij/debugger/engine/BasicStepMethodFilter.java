/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.psi.PsiMethod;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/26/13
 */
public class BasicStepMethodFilter implements MethodFilter{
  @NotNull
  protected final JVMName myDeclaringClassName;
  @NotNull
  private final String myTargetMethodName;
  @Nullable
  protected final JVMName myTargetMethodSignature;

  public BasicStepMethodFilter(PsiMethod psiMethod) {
    myDeclaringClassName = JVMNameUtil.getJVMQualifiedName(psiMethod.getContainingClass());
    myTargetMethodName = psiMethod.isConstructor() ? "<init>" : psiMethod.getName();
    myTargetMethodSignature = JVMNameUtil.getJVMSignature(psiMethod);
  }

  @NotNull
  public String getMethodName() {
    return myTargetMethodName;
  }

  public boolean locationMatches(DebugProcessImpl process, Location location) throws EvaluateException {
    final Method method = location.method();
    if (!myTargetMethodName.equals(method.name())) {
      return false;
    }
    if (myTargetMethodSignature != null) {
      if (!signatureMatches(method, myTargetMethodSignature.getName(process))) {
        return false;
      }
    }
    return DebuggerUtilsEx.isAssignableFrom(myDeclaringClassName.getName(process), location.declaringType());
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
}
