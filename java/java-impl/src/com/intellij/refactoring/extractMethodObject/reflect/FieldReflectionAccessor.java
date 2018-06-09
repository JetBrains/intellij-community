// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethodObject.reflect;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.ClassUtil;
import com.intellij.refactoring.extractMethodObject.ItemToReplaceDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * @author Vitaliy.Bibaev
 */
public class FieldReflectionAccessor extends ReferenceReflectionAccessorBase<FieldReflectionAccessor.FieldDescriptor> {
  private static final Logger LOG = Logger.getInstance(FieldReflectionAccessor.class);

  public FieldReflectionAccessor(@NotNull PsiClass psiClass,
                                 @NotNull PsiElementFactory elementFactory) {
    super(psiClass, elementFactory);
  }

  @Nullable
  @Override
  protected FieldDescriptor createDescriptor(@NotNull PsiReferenceExpression expression) {
    final PsiElement resolved = expression.resolve();
    if (resolved instanceof PsiField) {
      final PsiField field = (PsiField)resolved;
      String name = field.getName();
      if (name != null && !Objects.equals(field.getContainingClass(), getOuterClass()) && needReplace(field, expression)) {
        return new FieldDescriptor(field, expression);
      }
    }

    return null;
  }

  @Override
  protected void grantAccess(@NotNull FieldDescriptor descriptor) {
    PsiElement parent = descriptor.expression.getParent();
    if (parent instanceof PsiAssignmentExpression &&
        Objects.equals(descriptor.expression, ((PsiAssignmentExpression)parent).getLExpression())) {
      grantUpdateAccess((PsiAssignmentExpression)parent, descriptor);
    }
    else {
      grantReadAccess(descriptor);
    }
  }

  private void grantReadAccess(@NotNull FieldDescriptor descriptor) {
    PsiClass outerClass = getOuterClass();
    PsiMethod newMethod = createPsiMethod(descriptor, FieldAccessType.GET);
    if (newMethod == null) return;

    outerClass.add(newMethod);

    String qualifier = qualify(descriptor);
    String methodCall = newMethod.getName() + "(" + (qualifier == null ? "null" : qualifier) + ", null)";
    descriptor.expression.replace(getElementFactory().createExpressionFromText(methodCall, descriptor.expression));
  }

  private void grantUpdateAccess(@NotNull PsiAssignmentExpression assignmentExpression, @NotNull FieldDescriptor descriptor) {
    PsiClass outerClass = getOuterClass();
    PsiMethod newMethod = createPsiMethod(descriptor, FieldAccessType.SET);
    if (newMethod == null) return;

    outerClass.add(newMethod);
    PsiExpression rightExpression = assignmentExpression.getRExpression();
    if (rightExpression == null) {
      LOG.warn("Expression representing a new field value not found");
      return;
    }

    String newValue = rightExpression.getText();
    String qualifier = qualify(descriptor);
    String args = (qualifier == null ? "null" : qualifier) + ", " + newValue;
    String methodCallExpression = newMethod.getName() + "(" + args + ")";

    PsiExpression newMethodCallExpression = getElementFactory().createExpressionFromText(methodCallExpression, descriptor.expression);
    assignmentExpression.replace(newMethodCallExpression);
  }

  @Nullable
  private PsiMethod createPsiMethod(@NotNull FieldDescriptor descriptor, FieldAccessType accessType) {
    PsiClass outerClass = getOuterClass();
    PsiClass containingClass = descriptor.field.getContainingClass();
    String className = containingClass == null ? null : ClassUtil.getJVMClassName(containingClass);
    String fieldName = descriptor.field.getName();
    if (className == null || fieldName == null) {
      LOG.warn("Code is incomplete. Class name or field name not found");
      return null;
    }

    String methodName = PsiReflectionAccessUtil.getUniqueMethodName(outerClass, "accessToField" + StringUtil.capitalize(fieldName));
    ReflectionAccessMethodBuilder methodBuilder = new ReflectionAccessMethodBuilder(methodName);
    if (FieldAccessType.GET.equals(accessType)) {
      String returnType = PsiReflectionAccessUtil.getAccessibleReturnType(descriptor.expression, resolveFieldType(descriptor));
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

    return methodBuilder.build(getElementFactory(), outerClass);
  }

  private static boolean needReplace(@NotNull PsiField field, @NotNull PsiReferenceExpression expression) {
    return !PsiReflectionAccessUtil.isAccessibleMember(field) ||
           !PsiReflectionAccessUtil.isQualifierAccessible(expression.getQualifierExpression());
  }

  @NotNull
  private static PsiType resolveFieldType(@NotNull FieldDescriptor descriptor) {
    PsiType rawType = descriptor.field.getType();
    return descriptor.expression.advancedResolve(false).getSubstitutor().substitute(rawType);
  }

  @Nullable
  private static String qualify(@NotNull FieldDescriptor descriptor) {
    String qualifier = PsiReflectionAccessUtil.extractQualifier(descriptor.expression);
    if (qualifier == null) {
      if (!descriptor.field.hasModifierProperty(PsiModifier.STATIC)) {
        PsiClass containingClass = descriptor.field.getContainingClass();
        if (containingClass != null) {
          qualifier = containingClass.getQualifiedName() + ".this";
        }
      }
    }

    return qualifier;
  }

  public static class FieldDescriptor implements ItemToReplaceDescriptor {
    public final PsiField field;
    public final PsiReferenceExpression expression;

    public FieldDescriptor(@NotNull PsiField field, @NotNull PsiReferenceExpression expression) {
      this.field = field;
      this.expression = expression;
    }
  }
}
