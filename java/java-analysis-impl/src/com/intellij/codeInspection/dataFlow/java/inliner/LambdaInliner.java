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
package com.intellij.codeInspection.dataFlow.java.inliner;

import com.intellij.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.codeInspection.dataFlow.java.CFGBuilder;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import one.util.streamex.EntryStream;
import org.jetbrains.annotations.NotNull;

/**
 * An inliner which is capable to inline a call like ((IntSupplier)(() -> 5)).getAsInt() to the lambda body.
 * Works even if lambda body is complex, has several returns, etc.
 */
public class LambdaInliner implements CallInliner {
  @Override
  public boolean tryInlineCall(@NotNull CFGBuilder builder, @NotNull PsiMethodCallExpression call) {
    PsiExpression qualifier = PsiUtil.skipParenthesizedExprDown(call.getMethodExpression().getQualifierExpression());
    if (qualifier == null) return false;
    JavaResolveResult result = call.getMethodExpression().advancedResolve(false);
    PsiMethod method = (PsiMethod)result.getElement();
    if (method == null || method != LambdaUtil.getFunctionalInterfaceMethod(method.getContainingClass())) return false;
    if (method.isVarArgs()) return false; // TODO: support varargs
    PsiExpression[] args = call.getArgumentList().getExpressions();
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (args.length != parameters.length) return false;
    PsiSubstitutor substitutor = result.getSubstitutor();
    return builder.tryInlineLambda(args.length, qualifier, DfaPsiUtil.getTypeNullability(substitutor.substitute(method.getReturnType())),
                                   () -> EntryStream.zip(args, parameters)
                                     .forKeyValue((arg, parameter) -> builder.pushExpression(arg).boxUnbox(arg, parameter.getType())));
  }
}
