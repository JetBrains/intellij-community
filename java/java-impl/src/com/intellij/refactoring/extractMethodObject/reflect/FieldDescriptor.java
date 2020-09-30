// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
public final class FieldDescriptor implements ItemToReplaceDescriptor {
  private static final Logger LOG = Logger.getInstance(FieldDescriptor.class);

  private final PsiField myField;
  private final PsiReferenceExpression myExpression;
  private final String myAccessibleType;

  private FieldDescriptor(@NotNull PsiField field, @NotNull PsiReferenceExpression expression) {
    myField = field;
    myExpression = expression;
    String fieldType = PsiReflectionAccessUtil.getAccessibleReturnType(myExpression, resolveFieldType(myField, myExpression));
    if (fieldType == null) {
      LOG.warn("Could not resolve field type. java.lang.Object will be used instead");
      fieldType = "java.lang.Object";
    }
    myAccessibleType = fieldType;
  }

  @Nullable
  public static ItemToReplaceDescriptor createIfInaccessible(@NotNull PsiClass outerClass, @NotNull PsiReferenceExpression expression) {
    final PsiElement resolved = expression.resolve();
    if (resolved instanceof PsiField) {
      final PsiField field = (PsiField)resolved;
      PsiClass containingClass = field.getContainingClass();


      if (!Objects.equals(containingClass, outerClass) && needReplace(outerClass, field, expression)) {
        Array.getLength(new int[3]);
        return new FieldDescriptor(field, expression);
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
      grantUpdateAccess((PsiAssignmentExpression)parent, outerClass, callExpression, elementFactory);
    }
    else {
      grantReadAccess(outerClass, callExpression, elementFactory);
    }
  }

  private void grantReadAccess(@NotNull PsiClass outerClass,
                               @NotNull PsiMethodCallExpression generatedCall,
                               @NotNull PsiElementFactory elementFactory) {
    PsiMethod newMethod = createPsiMethod(FieldAccessType.GET, outerClass, elementFactory);
    if (newMethod == null) return;

    outerClass.add(newMethod);

    String object = MemberQualifierUtil.findObjectExpression(myExpression, myField, outerClass, generatedCall, elementFactory);
    String methodCall = newMethod.getName() + "(" + (object == null ? "null" : object) + ", null)";
    myExpression.replace(elementFactory.createExpressionFromText(methodCall, myExpression));
  }

  private void grantUpdateAccess(@NotNull PsiAssignmentExpression assignmentExpression,
                                 @NotNull PsiClass outerClass,
                                 @NotNull PsiMethodCallExpression generatedCall,
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
    String objectForReference = MemberQualifierUtil.findObjectExpression(myExpression, myField, outerClass, generatedCall, elementFactory);
    String args = (objectForReference == null ? "null" : objectForReference) + ", " + newValue;
    String methodCallExpression = newMethod.getName() + "(" + args + ")";

    PsiExpression newMethodCallExpression = elementFactory.createExpressionFromText(methodCallExpression, myExpression);
    assignmentExpression.replace(newMethodCallExpression);
  }

  @Nullable
  private PsiMethod createPsiMethod(FieldAccessType accessType, PsiClass outerClass, PsiElementFactory elementFactory) {
    PsiClass containingClass = myField.getContainingClass();
    String className = containingClass == null ? null : ClassUtil.getJVMClassName(containingClass);
    String fieldName = myField.getName();
    if (className == null) {
      LOG.warn("Code is incomplete. Class name or field name not found");
      return null;
    }

    String methodName = PsiReflectionAccessUtil.getUniqueMethodName(outerClass, "accessToField" + StringUtil.capitalize(fieldName));
    ReflectionAccessMethodBuilder methodBuilder = new ReflectionAccessMethodBuilder(methodName);
    if (FieldAccessType.GET.equals(accessType)) {
      methodBuilder.accessedField(className, fieldName).setReturnType(myAccessibleType);
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

  private static boolean needReplace(@NotNull PsiClass outerClass, @NotNull PsiField field, @NotNull PsiReferenceExpression expression) {
    return !PsiReflectionAccessUtil.isAccessibleMember(field, outerClass, expression.getQualifierExpression());
  }

  @NotNull
  private static PsiType resolveFieldType(@NotNull PsiField field, @NotNull PsiReferenceExpression referenceExpression) {
    PsiType rawType = field.getType();
    return referenceExpression.advancedResolve(false).getSubstitutor().substitute(rawType);
  }
}
