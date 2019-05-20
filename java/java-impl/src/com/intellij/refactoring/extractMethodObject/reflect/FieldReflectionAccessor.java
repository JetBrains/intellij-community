// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethodObject.reflect;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.ClassUtil;
import com.intellij.refactoring.extractMethodObject.ItemToReplaceDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.util.Objects;

/**
 * @author Vitaliy.Bibaev
 */
public class FieldReflectionAccessor implements ItemToReplaceDescriptor {
  private static final Logger LOG = Logger.getInstance(FieldReflectionAccessor.class);

  private final PsiField myField;
  private final PsiReferenceExpression myExpression;

  private FieldReflectionAccessor(@NotNull PsiField field, @NotNull PsiReferenceExpression expression) {
    myField = field;
    myExpression = expression;
  }

  @Nullable
  public static ItemToReplaceDescriptor createIfInaccessible(@NotNull PsiClass outerClass, @NotNull PsiReferenceExpression expression) {
    final PsiElement resolved = expression.resolve();
    if (resolved instanceof PsiField) {
      final PsiField field = (PsiField)resolved;
      PsiClass containingClass = field.getContainingClass();


      if (!Objects.equals(containingClass, outerClass) && needReplace(field, expression)) {
        Array.getLength(new int[3]);
        return new FieldReflectionAccessor(field, expression);
      }
    }

    return null;
  }

  @Override
  public void replace(@NotNull PsiClass outerClass,
                      @NotNull PsiElementFactory elementFactory,
                      @NotNull PsiMethodCallExpression callExpression) {
    PsiElement parent = myExpression.getParent();
    if (parent instanceof PsiAssignmentExpression &&
        Objects.equals(myExpression, ((PsiAssignmentExpression)parent).getLExpression())) {
      grantUpdateAccess((PsiAssignmentExpression)parent, outerClass, elementFactory);
    }
    else {
      grantReadAccess(outerClass, elementFactory);
    }
  }

  private void grantReadAccess(@NotNull PsiClass outerClass, @NotNull PsiElementFactory elementFactory) {
    PsiMethod newMethod = createPsiMethod(FieldAccessType.GET, outerClass, elementFactory);
    if (newMethod == null) return;

    outerClass.add(newMethod);

    String qualifier = qualify();
    String methodCall = newMethod.getName() + "(" + (qualifier == null ? "null" : qualifier) + ", null)";
    myExpression.replace(elementFactory.createExpressionFromText(methodCall, myExpression));
  }

  private void grantUpdateAccess(@NotNull PsiAssignmentExpression assignmentExpression,
                                 @NotNull PsiClass outerClass,
                                 @NotNull PsiElementFactory elementFactory) {
    PsiMethod newMethod = createPsiMethod(FieldAccessType.SET, outerClass, elementFactory);
    if (newMethod == null) return;

    outerClass.add(newMethod);
    PsiExpression rightExpression = assignmentExpression.getRExpression();
    if (rightExpression == null) {
      LOG.warn("Expression representing a new field value not found");
      return;
    }

    String newValue = rightExpression.getText();
    String qualifier = qualify();
    String args = (qualifier == null ? "null" : qualifier) + ", " + newValue;
    String methodCallExpression = newMethod.getName() + "(" + args + ")";

    PsiExpression newMethodCallExpression = elementFactory.createExpressionFromText(methodCallExpression, myExpression);
    assignmentExpression.replace(newMethodCallExpression);
  }

  @Nullable
  private PsiMethod createPsiMethod(FieldAccessType accessType, PsiClass outerClass, PsiElementFactory elementFactory) {
    PsiClass containingClass = myField.getContainingClass();
    String className = containingClass == null ? null : ClassUtil.getJVMClassName(containingClass);
    String fieldName = myField.getName();
    if (className == null || fieldName == null) {
      LOG.warn("Code is incomplete. Class name or field name not found");
      return null;
    }

    String methodName = PsiReflectionAccessUtil.getUniqueMethodName(outerClass, "accessToField" + StringUtil.capitalize(fieldName));
    ReflectionAccessMethodBuilder methodBuilder = new ReflectionAccessMethodBuilder(methodName);
    if (FieldAccessType.GET.equals(accessType)) {
      String returnType = PsiReflectionAccessUtil.getAccessibleReturnType(myExpression, resolveFieldType());
      if (returnType == null) {
        LOG.warn("Could not resolve field type");
        return null;
      }
      methodBuilder.accessedField(className, fieldName)
                   .setReturnType(returnType);
    }
    else {
      methodBuilder.updatedField(className, fieldName)
                   .setReturnType("void");
    }

    methodBuilder.setStatic(outerClass.hasModifierProperty(PsiModifier.STATIC))
                 .addParameter("java.lang.Object", "object")
                 .addParameter("java.lang.Object", "value");

    return methodBuilder.build(elementFactory, outerClass);
  }

  private static boolean needReplace(@NotNull PsiField field, @NotNull PsiReferenceExpression expression) {
    return !PsiReflectionAccessUtil.isAccessibleMember(field) ||
           !PsiReflectionAccessUtil.isQualifierAccessible(expression.getQualifierExpression());
  }

  @NotNull
  private PsiType resolveFieldType() {
    PsiType rawType = myField.getType();
    return myExpression.advancedResolve(false).getSubstitutor().substitute(rawType);
  }

  @Nullable
  private String qualify() {
    String qualifier = PsiReflectionAccessUtil.extractQualifier(myExpression);
    if (qualifier == null) {
      if (!myField.hasModifierProperty(PsiModifier.STATIC)) {
        PsiClass containingClass = myField.getContainingClass();
        if (containingClass != null) {
          qualifier = containingClass.getQualifiedName() + ".this";
        }
      }
    }

    return qualifier;
  }
}
