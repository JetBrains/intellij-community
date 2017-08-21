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
package com.intellij.codeInspection.dataFlow.inliner;

import com.intellij.codeInspection.dataFlow.CFGBuilder;
import com.intellij.codeInspection.dataFlow.Nullness;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import one.util.streamex.EntryStream;
import org.jetbrains.annotations.NotNull;

/**
 * An inliner which is capable to inline a call like ((IntSupplier)(() -> 5)).getAsInt() to the lambda body.
 * Works even if lambda body is complex, has several returns, etc.
 */
public class LambdaInliner implements CallInliner {
  @Override
  public boolean tryInlineCall(@NotNull CFGBuilder builder, @NotNull PsiMethodCallExpression call) {
    PsiMethod method = call.resolveMethod();
    if (method == null || method != LambdaUtil.getFunctionalInterfaceMethod(method.getContainingClass())) return false;
    PsiTypeCastExpression typeCastExpression = ObjectUtils
      .tryCast(PsiUtil.skipParenthesizedExprDown(call.getMethodExpression().getQualifierExpression()), PsiTypeCastExpression.class);
    if (typeCastExpression == null) return false;
    PsiLambdaExpression lambda =
      ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(typeCastExpression.getOperand()), PsiLambdaExpression.class);
    if (lambda == null || lambda.getBody() == null) return false;
    if (method.isVarArgs()) return false; // TODO: support varargs
    PsiExpression[] args = call.getArgumentList().getExpressions();
    PsiParameter[] parameters = lambda.getParameterList().getParameters();
    if (args.length != parameters.length) return false;
    EntryStream.zip(args, parameters).forKeyValue((arg, parameter) ->
                                                    builder.pushVariable(parameter)
                                                      .pushExpression(arg)
                                                      .boxUnbox(arg, parameter.getType())
                                                      .assign()
                                                      .pop());
    builder.inlineLambda(lambda, Nullness.UNKNOWN);
    return true;
  }
}
