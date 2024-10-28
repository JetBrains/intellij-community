// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.java.inliner;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullabilityAnnotationInfo;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.dataFlow.NullabilityProblemKind;
import com.intellij.codeInspection.dataFlow.java.CFGBuilder;
import com.intellij.codeInspection.dataFlow.java.JavaDfaValueFactory;
import com.intellij.codeInspection.dataFlow.jvm.descriptors.GetterDescriptor;
import com.intellij.codeInspection.dataFlow.jvm.descriptors.PlainDescriptor;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.codeInspection.util.OptionalUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
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
    return tryInlineGetter(builder, call, method) ||
           tryInlineSetter(builder, call, method);
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

  private static boolean tryInlineGetter(@NotNull CFGBuilder builder, @NotNull PsiMethodCallExpression call, PsiMethod method) {
    PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) return false;
    String qualifiedName = containingClass.getQualifiedName();
    // Methods Enum.name() and Enum.ordinal() are handled especially
    if (CommonClassNames.JAVA_LANG_ENUM.equals(qualifiedName)) return false;
    // Unboxing calls like Boolean.booleanValue() are handled especially
    if (qualifiedName != null && TypeConversionUtil.isPrimitiveWrapper(qualifiedName)) return false;
    // Avoid inlining OptionalInt.isPresent(), etc.
    if (OptionalUtil.isJdkOptionalClassName(qualifiedName)) return false;
    // Known stable methods (like methods from reflection) may read non-final fields,
    // so inlining them breaks the stability
    if (GetterDescriptor.isKnownStableMethod(method)) return false;
    PsiField field = PropertyUtil.getFieldOfGetter(method);
    if (field == null) return false;
    DfaValue value = JavaDfaValueFactory.getQualifierOrThisValue(builder.getFactory(), call.getMethodExpression());
    if (value == null) return false;
    NullableNotNullManager manager = NullableNotNullManager.getInstance(method.getProject());
    NullabilityAnnotationInfo methodNullability = manager.findEffectiveNullabilityInfo(method);
    NullabilityAnnotationInfo fieldNullability = manager.findEffectiveNullabilityInfo(field);
    if (methodNullability != null && methodNullability.getNullability() == Nullability.NULLABLE &&
        (fieldNullability == null || fieldNullability.getNullability() != Nullability.NULLABLE)) {
      // Avoid inlining if getter is marked as nullable, while the field is not.
      // In this rare case, we cannot preserve the nullability warning on the callsite.
      return false;
    }
    boolean nonNull = methodNullability != null && methodNullability.getNullability() == Nullability.NOT_NULL && !methodNullability.isInferred();
    PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
    if (qualifier != null && !(qualifier instanceof PsiReferenceExpression ref && ref.resolve() instanceof PsiClass)) {
      builder.pushExpression(qualifier).pop();
    }
    builder.push(new PlainDescriptor(field).createValue(builder.getFactory(), value), call);
    if (nonNull) {
      builder.nullCheck(NullabilityProblemKind.assumeNotNull.problem(call, call));
    }
    return true;
  }
}
