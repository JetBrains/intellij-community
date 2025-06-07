// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.java.inliner;

import com.intellij.codeInspection.dataFlow.java.CFGBuilder;
import com.intellij.codeInspection.dataFlow.java.JavaDfaValueFactory;
import com.intellij.codeInspection.dataFlow.jvm.descriptors.PlainDescriptor;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Inlines accessors to read fields directly
 */
public final class AccessorInliner implements CallInliner {
  @Override
  public boolean tryInlineCall(@NotNull CFGBuilder builder, @NotNull PsiMethodCallExpression call) {
    PsiMethod method = call.resolveMethod();
    if (method == null) return false;
    if (PsiUtil.canBeOverridden(method)) return false;
    return tryInlineSetter(builder, call, method);
  }

  private static boolean tryInlineSetter(@NotNull CFGBuilder builder, @NotNull PsiMethodCallExpression call, PsiMethod method) {
    if (!PsiTypes.voidType().equals(method.getReturnType())) return false;
    PsiField field = PropertyUtil.getFieldOfSetter(method);
    if (field == null) return false;
    DfaValue value = JavaDfaValueFactory.getQualifierOrThisValue(builder.getFactory(), call.getMethodExpression());
    if (value == null) return false;
    PsiExpression[] args = call.getArgumentList().getExpressions();
    if (args.length != 1) return false;
    DfaValue fieldValue = new PlainDescriptor(field).createValue(builder.getFactory(), value);
    if (!(fieldValue instanceof DfaVariableValue fieldVar)) return false;
    PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
    if (qualifier != null && !(qualifier instanceof PsiReferenceExpression ref && ref.resolve() instanceof PsiClass)) {
      builder.pushExpression(qualifier).pop();
    }
    builder.pushExpression(args[0]).assignTo(fieldVar);
    // Technically, we should pop and pushUnknown here, but the result of void method is ignored anyway, 
    // so we can spare two instructions
    return true;
  }
}
