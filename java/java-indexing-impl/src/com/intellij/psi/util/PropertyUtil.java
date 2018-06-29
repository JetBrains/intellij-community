// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util;

import com.intellij.lang.java.beans.PropertyKind;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaSimplePropertyIndexKt;
import com.intellij.psi.impl.source.PsiMethodImpl;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * @author Mike
 */
public class PropertyUtil extends PropertyUtilBase {
  private PropertyUtil() {
  }

  @Nullable
  public static PsiField getFieldOfGetter(PsiMethod method) {
    return getFieldOfGetter(method, true);
  }

  @Nullable
  private static PsiField getFieldOfGetter(PsiMethod method, boolean useIndex) {
    PsiField field = useIndex && method instanceof PsiMethodImpl && method.isPhysical()
            ? JavaSimplePropertyIndexKt.getFieldOfGetter((PsiMethodImpl)method)
            : getSimplyReturnedField(getGetterReturnExpression(method));
    if (field == null || !checkFieldLocation(method, field)) return null;
    final PsiType returnType = method.getReturnType();
    return returnType != null && field.getType().equals(returnType) ? field : null;
  }

  @Nullable
  public static PsiField getSimplyReturnedField(@Nullable PsiExpression value) {
    if (!(value instanceof PsiReferenceExpression)) {
      return null;
    }

    final PsiReferenceExpression reference = (PsiReferenceExpression)value;
    if (hasSubstantialQualifier(reference)) {
      return null;
    }

    final PsiElement referent = reference.resolve();
    if (!(referent instanceof PsiField)) {
      return null;
    }

    return (PsiField)referent;
  }

  private static boolean hasSubstantialQualifier(PsiReferenceExpression reference) {
    final PsiExpression qualifier = reference.getQualifierExpression();
    if (qualifier == null) return false;

    if (qualifier instanceof PsiThisExpression || qualifier instanceof PsiSuperExpression) {
      return false;
    }

    if (qualifier instanceof PsiReferenceExpression) {
      return !(((PsiReferenceExpression)qualifier).resolve() instanceof PsiClass);
    }
    return true;
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
      field = JavaSimplePropertyIndexKt.getFieldOfSetter((PsiMethodImpl)method);
    }
    else {
      @NonNls final String name = method.getName();
      if (!name.startsWith(SET_PREFIX)) {
        return null;
      }
      final PsiCodeBlock body = method.getBody();
      if (body == null) {
        return null;
      }
      final PsiStatement[] statements = body.getStatements();
      if (statements.length != 1) {
        return null;
      }
      final PsiStatement statement = statements[0];
      if (!(statement instanceof PsiExpressionStatement)) {
        return null;
      }
      final PsiExpressionStatement possibleAssignmentStatement = (PsiExpressionStatement)statement;
      final PsiExpression possibleAssignment = possibleAssignmentStatement.getExpression();
      if (!(possibleAssignment instanceof PsiAssignmentExpression)) {
        return null;
      }
      final PsiAssignmentExpression assignment = (PsiAssignmentExpression)possibleAssignment;
      if (!JavaTokenType.EQ.equals(assignment.getOperationTokenType())) {
        return null;
      }
      final PsiExpression lhs = assignment.getLExpression();
      if (!(lhs instanceof PsiReferenceExpression)) {
        return null;
      }
      final PsiReferenceExpression reference = (PsiReferenceExpression)lhs;
      final PsiExpression qualifier = reference.getQualifierExpression();
      if (qualifier instanceof PsiReferenceExpression) {
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)qualifier;
        final PsiElement target = referenceExpression.resolve();
        if (!(target instanceof PsiClass)) {
          return null;
        }
      }
      else if (qualifier != null && !(qualifier instanceof PsiThisExpression) && !(qualifier instanceof PsiSuperExpression)) {
        return null;
      }
      final PsiElement referent = reference.resolve();
      if (referent == null) {
        return null;
      }
      if (!(referent instanceof PsiField)) {
        return null;
      }
      field = (PsiField)referent;

      final PsiExpression rhs = assignment.getRExpression();
      if (!(rhs instanceof PsiReferenceExpression)) {
        return null;
      }
      final PsiReferenceExpression rReference = (PsiReferenceExpression)rhs;
      final PsiExpression rQualifier = rReference.getQualifierExpression();
      if (rQualifier != null) {
        return null;
      }
      final PsiElement rReferent = rReference.resolve();
      if (rReferent == null) {
        return null;
      }
      if (!(rReferent instanceof PsiParameter)) {
        return null;
      }
    }
    return field != null && field.getType().equals(parameterList.getParameters()[0].getType()) && checkFieldLocation(method, field)
           ? field
           : null;
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
    return PsiResolveHelper.SERVICE.getInstance(method.getProject()).isAccessible(field, method, null) &&
           (!method.hasModifier(JvmModifier.STATIC) || field.hasModifier(JvmModifier.STATIC)) &&
           InheritanceUtil.isInheritorOrSelf(method.getContainingClass(), field.getContainingClass(), true);
  }

}
