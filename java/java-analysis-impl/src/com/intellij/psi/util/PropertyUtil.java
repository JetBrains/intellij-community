// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.codeInspection.bytecodeAnalysis.ProjectBytecodeAnalysis;
import com.intellij.lang.java.beans.PropertyKind;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaSimplePropertyGistKt;
import com.intellij.psi.impl.compiled.ClsMethodImpl;
import com.intellij.psi.impl.light.LightRecordMethod;
import com.intellij.psi.impl.source.PsiMethodImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.function.Supplier;

public final class PropertyUtil extends PropertyUtilBase {
  private PropertyUtil() {
  }

  @Nullable
  public static PsiField getFieldOfGetter(PsiMethod method) {
    return getFieldOfGetter(method, true);
  }

  @Nullable
  private static PsiField getFieldOfGetter(PsiMethod method, boolean useIndex) {
    return getFieldOfGetter(method, () -> getGetterReturnExpression(method), useIndex);
  }

  @Nullable
  public static PsiField getFieldOfGetter(PsiMethod method, Supplier<? extends PsiExpression> returnExprSupplier, boolean useIndex) {
    PsiField field = getFieldImpl(method, returnExprSupplier, useIndex);
    if (field == null || !checkFieldLocation(method, field)) return null;
    final PsiType returnType = method.getReturnType();
    return returnType != null && field.getType().equals(returnType) ? field : null;
  }

  private static @Nullable PsiField getFieldImpl(@NotNull PsiMethod method,
                                                 @NotNull Supplier<? extends PsiExpression> returnExprSupplier,
                                                 boolean useIndex) {
    if (!method.getParameterList().isEmpty()) return null;
    if (method instanceof LightRecordMethod) {
      PsiRecordComponent component = JavaPsiRecordUtil.getRecordComponentForAccessor(method);
      return component == null ? null : JavaPsiRecordUtil.getFieldForComponent(component);
    }
    if (useIndex) {
      if (PsiUtil.preferCompiledElement(method) instanceof ClsMethodImpl compiledMethod) {
        return ProjectBytecodeAnalysis.getInstance(method.getProject()).findFieldForAccessor(compiledMethod);
      }
      if (method instanceof PsiMethodImpl && method.isPhysical()) {
        return JavaSimplePropertyGistKt.getFieldOfGetter(method);
      }
    }
    return getSimplyReturnedField(returnExprSupplier.get());
  }

  public static boolean isSimpleGetter(@Nullable PsiMethod method) {
    //noinspection TestOnlyProblems
    return isSimpleGetter(method, true);
  }

  @TestOnly
  public static boolean isSimpleGetter(@Nullable PsiMethod method, boolean useIndex) {
    return getFieldOfGetter(method, useIndex) != null;
  }

  @Nullable
  public static PsiField getFieldOfSetter(@Nullable PsiMethod method) {
    return getFieldOfSetter(method, true);
  }

  @Nullable
  private static PsiField getFieldOfSetter(@Nullable PsiMethod method, boolean useIndex) {
    if (method == null) {
      return null;
    }
    final PsiParameterList parameterList = method.getParameterList();
    if (parameterList.getParametersCount() != 1) {
      return null;
    }

    PsiField field;
    if (useIndex && method instanceof PsiMethodImpl && method.isPhysical()) {
      field = JavaSimplePropertyGistKt.getFieldOfSetter(method);
    }
    else if (useIndex && method instanceof ClsMethodImpl) {
      field = ProjectBytecodeAnalysis.getInstance(method.getProject()).findFieldForAccessor(method);
    }
    else {
      field = getFieldFromSetterExplicit(method);
    }
    if (field == null) return null;
    return field.getType().equals(parameterList.getParameters()[0].getType()) && 
           checkFieldLocation(method, field) ? field : null;
  }

  private static @Nullable PsiField getFieldFromSetterExplicit(@NotNull PsiMethod method) {
    String name = method.getName();
    if (!name.startsWith(SET_PREFIX)) return null;
    PsiCodeBlock body = method.getBody();
    if (body == null) return null;
    PsiStatement[] statements = body.getStatements();
    if (statements.length != 1 || !(statements[0] instanceof PsiExpressionStatement possibleAssignmentStatement)) return null;
    PsiExpression possibleAssignment = possibleAssignmentStatement.getExpression();
    if (!(possibleAssignment instanceof PsiAssignmentExpression assignment)) return null;
    if (!JavaTokenType.EQ.equals(assignment.getOperationTokenType())) return null;
    PsiExpression lhs = PsiUtil.skipParenthesizedExprDown(assignment.getLExpression());
    if (!(lhs instanceof PsiReferenceExpression reference)) return null;
    PsiExpression qualifier = PsiUtil.skipParenthesizedExprDown(reference.getQualifierExpression());
    boolean allowedQualifier =
      qualifier == null || qualifier instanceof PsiQualifiedExpression ||
      (qualifier instanceof PsiReferenceExpression ref && ref.resolve() instanceof PsiClass);
    if (!allowedQualifier) return null;
    if (!(reference.resolve() instanceof PsiField field)) return null;

    PsiExpression rhs = PsiUtil.skipParenthesizedExprDown(assignment.getRExpression());
    if (!(rhs instanceof PsiReferenceExpression rReference)) return null;
    if (rReference.getQualifierExpression() != null) return null;
    if (!(rReference.resolve() instanceof PsiParameter)) return null;
    return field;
  }

  public static boolean isSimpleSetter(@Nullable PsiMethod method) {
    //noinspection TestOnlyProblems
    return isSimpleSetter(method, true);
  }

  @TestOnly
  public static boolean isSimpleSetter(@Nullable PsiMethod method, boolean useIndex) {
    return getFieldOfSetter(method, useIndex) != null;
  }

  @Nullable
  public static PsiMethod getReversePropertyMethod(PsiMethod propertyMethod) {
    if (propertyMethod == null) {
      return null;
    }
    final PsiClass aClass = propertyMethod.getContainingClass();
    if (aClass == null) {
      return null;
    }
    final String methodName = propertyMethod.getName();
    final PropertyKind kind = getPropertyKind(propertyMethod.getName());
    if (kind == null) {
      return null;
    }
    final String name = methodName.substring(kind.prefix.length());
    final PsiField field = kind == PropertyKind.SETTER ? getFieldOfSetter(propertyMethod) : getFieldOfGetter(propertyMethod);
    if (field == null) {
      return null;
    }
    if (kind == PropertyKind.SETTER) {
      final PsiMethod result = findPropertyMethod(aClass, PropertyKind.GETTER, name, field);
      if (result != null) {
        return result;
      }
      return findPropertyMethod(aClass, PropertyKind.BOOLEAN_GETTER, name, field);
    }
    else {
      return findPropertyMethod(aClass, PropertyKind.SETTER, name, field);
    }
  }

  private static PsiMethod findPropertyMethod(@NotNull PsiClass aClass,
                                              @NotNull PropertyKind kind,
                                              @NotNull String propertyName,
                                              @NotNull PsiField field1) {
    final PsiMethod[] methods = aClass.findMethodsByName(kind.prefix + propertyName, true);
    for (PsiMethod method : methods) {
      final PsiField field2 = kind == PropertyKind.SETTER ? getFieldOfSetter(method) : getFieldOfGetter(method);
      if (field1.equals(field2)) {
        return method;
      }
    }
    return null;
  }

  private static boolean checkFieldLocation(PsiMethod method, PsiField field) {
    return PsiResolveHelper.getInstance(method.getProject()).isAccessible(field, method, null) &&
           (!method.hasModifier(JvmModifier.STATIC) || field.hasModifier(JvmModifier.STATIC)) &&
           InheritanceUtil.isInheritorOrSelf(method.getContainingClass(), field.getContainingClass(), true);
  }

}
