// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.bugs;

import com.intellij.psi.*;
import com.intellij.psi.util.JavaPsiRecordUtil;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.JavaPsiConstructorUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Bas Leijdekkers
 */
public final class CopyConstructorMissesFieldInspection extends BaseInspection {

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final List<PsiField> fields = (List<PsiField>)infos[0];
    if (fields.size() == 1) {
      return InspectionGadgetsBundle.message("copy.constructor.misses.field.problem.descriptor.1", fields.get(0).getName());
    }
    else if (fields.size() == 2) {
      return InspectionGadgetsBundle.message("copy.constructor.misses.field.problem.descriptor.2",
                                             fields.get(0).getName(), fields.get(1).getName());
    }
    else if (fields.size() == 3) {
      return InspectionGadgetsBundle.message("copy.constructor.misses.field.problem.descriptor.3",
                                             fields.get(0).getName(), fields.get(1).getName(), fields.get(2).getName());
    }
    return InspectionGadgetsBundle.message("copy.constructor.misses.field.problem.descriptor.many", fields.size());
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CopyConstructorMissesFieldVisitor();
  }

  private static class CopyConstructorMissesFieldVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      if (!MethodUtils.isCopyConstructor(method)) {
        return;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return;
      }
      final List<PsiField> fields = new ArrayList<>(ContainerUtil.filter(aClass.getFields(),
                                                         f -> !f.hasModifierProperty(PsiModifier.STATIC) &&
                                                              !f.hasModifierProperty(PsiModifier.TRANSIENT) &&
                                                              (!f.hasModifierProperty(PsiModifier.FINAL) || f.getInitializer() == null)));
      if (fields.isEmpty()) return;
      final PsiParameter parameter = Objects.requireNonNull(method.getParameterList().getParameter(0));
      final List<PsiField> assignedFields = new SmartList<>();
      final Set<PsiMethod> methodsOneLevelDeep = new HashSet<>();
      if (!PsiTreeUtil.processElements(method, e -> collectAssignedFields(e, parameter, methodsOneLevelDeep, assignedFields))) {
        return;
      }
      if (aClass.isRecord() && ContainerUtil.exists(methodsOneLevelDeep, m -> JavaPsiRecordUtil.isCanonicalConstructor(m))) {
        return;
      }
      for (PsiMethod calledMethod : methodsOneLevelDeep) {
        if (!PsiTreeUtil.processElements(calledMethod, e -> collectAssignedFields(e, null, null, assignedFields))) {
          return;
        }
      }
      for (PsiField assignedField : assignedFields) {
        if (aClass == PsiUtil.resolveClassInClassTypeOnly(assignedField.getType())) {
          return;
        }
      }
      fields.removeAll(assignedFields);
      if (fields.isEmpty()) {
        return;
      }
      registerMethodError(method, fields);
    }

    private static boolean collectAssignedFields(PsiElement element, PsiParameter parameter,
                                                 @Nullable Set<? super PsiMethod> methods, List<? super PsiField> assignedFields) {
      if (element instanceof PsiAssignmentExpression) {
        final PsiExpression lhs = PsiUtil.skipParenthesizedExprDown(((PsiAssignmentExpression)element).getLExpression());
        final PsiVariable variable = resolveVariable(lhs, null);
        if (variable instanceof PsiField) {
          assignedFields.add((PsiField)variable);
        }
      }
      else if (JavaPsiConstructorUtil.isChainedConstructorCall(element)) {
        final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)element;
        for (PsiExpression argument : methodCallExpression.getArgumentList().getExpressions()) {
          argument = PsiUtil.skipParenthesizedExprDown(argument);
          final PsiVariable variable = resolveVariable(argument, parameter);
          if (variable == parameter) {
            // instance to copy is passed to another constructor
            return false;
          }
        }
        if (methods != null) {
          final PsiMethod constructor = methodCallExpression.resolveMethod();
          if (constructor != null) {
            methods.add(constructor);
          }
        }
      }
      else if (element instanceof PsiMethodCallExpression) {
        final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)element;
        final PsiExpression qualifier =
          PsiUtil.skipParenthesizedExprDown(methodCallExpression.getMethodExpression().getQualifierExpression());
        if (qualifier == null || qualifier instanceof PsiThisExpression) {
          final PsiMethod method = methodCallExpression.resolveMethod();
          final PsiField field = PropertyUtil.getFieldOfSetter(method);
          if (field != null) {
            // field assigned using setter
            assignedFields.add(field);
          }
          else if (methods != null && method != null) {
            methods.add(method);
          }
        }
        else if (qualifier instanceof PsiReferenceExpression referenceExpression) {
          // consider field assigned if method is called on it.
          final PsiElement target = referenceExpression.resolve();
          if (target instanceof PsiField) {
            assignedFields.add((PsiField)target);
          }
        }
      }
      return true;
    }

    private static PsiVariable resolveVariable(PsiExpression expression, @Nullable PsiParameter requiredQualifier) {
      if (!(expression instanceof PsiReferenceExpression referenceExpression)) {
        return null;
      }
      final PsiExpression qualifier = PsiUtil.skipParenthesizedExprDown(referenceExpression.getQualifierExpression());
      final PsiElement target = referenceExpression.resolve();
      if (requiredQualifier == null) {
        if (!(qualifier == null || qualifier instanceof PsiThisExpression)) {
          return null;
        }
      }
      else if (!ExpressionUtils.isReferenceTo(qualifier, requiredQualifier)) {
        return target == requiredQualifier ? requiredQualifier : null;
      }
      return target instanceof PsiVariable ? (PsiVariable)target : null;
    }
  }
}
