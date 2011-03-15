/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.refactoring.psi;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PropertyUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public class PropertyUtils {
    private PropertyUtils() {
    }

    public static PsiMethod findSetterForField(PsiField field) {
        final PsiClass containingClass = field.getContainingClass();
        final Project project = field.getProject();
        final String propertyName = PropertyUtil.suggestPropertyName(project, field);
        final boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
        return PropertyUtil.findPropertySetter(containingClass, propertyName, isStatic, true);
    }

    public static PsiMethod findGetterForField(PsiField field) {
        final PsiClass containingClass = field.getContainingClass();
        final Project project = field.getProject();
        final String propertyName = PropertyUtil.suggestPropertyName(project, field);
        final boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
        return PropertyUtil.findPropertyGetter(containingClass, propertyName, isStatic, true);
    }

  @Nullable
  public static PsiField getFieldOfGetter(PsiMethod method) {
    if (method == null) {
      return null;
    }
    final PsiParameterList parameterList = method.getParameterList();
    if (parameterList.getParametersCount() != 0) {
      return null;
    }
    @NonNls final String name = method.getName();
    if (!name.startsWith("get") && !name.startsWith("is")) {
      return null;
    }
    if (method.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
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
    if (!(statement instanceof PsiReturnStatement)) {
      return null;
    }
    final PsiReturnStatement returnStatement =
      (PsiReturnStatement)statement;
    final PsiExpression value = returnStatement.getReturnValue();
    if (value == null) {
      return null;
    }
    if (!(value instanceof PsiReferenceExpression)) {
      return null;
    }
    final PsiReferenceExpression reference = (PsiReferenceExpression)value;
    final PsiExpression qualifier = reference.getQualifierExpression();
    if (qualifier != null && !(qualifier instanceof PsiThisExpression) && !(qualifier instanceof PsiSuperExpression)) {
      return null;
    }
    final PsiElement referent = reference.resolve();
    if (referent == null) {
      return null;
    }
    if (!(referent instanceof PsiField)) {
      return null;
    }
    final PsiField field = (PsiField)referent;
    final PsiType fieldType = field.getType();
    final PsiType returnType = method.getReturnType();
    if (returnType == null) {
      return null;
    }
    if (!fieldType.equalsToText(returnType.getCanonicalText())) {
      return null;
    }
    final PsiClass fieldContainingClass = field.getContainingClass();
    final PsiClass methodContainingClass = method.getContainingClass();
    if (InheritanceUtil.isInheritorOrSelf(methodContainingClass, fieldContainingClass, true)) {
      return field;
    }
    else {
      return null;
    }
  }

  public static boolean isSimpleGetter(PsiMethod method) {
    return getFieldOfGetter(method) != null;
  }

  @Nullable
  public static PsiField getFieldOfSetter(PsiMethod method) {
    if (method == null) {
      return null;
    }
    final PsiParameterList parameterList = method.getParameterList();
    if (parameterList.getParametersCount() != 1) {
      return null;
    }
    @NonNls final String name = method.getName();
    if (!name.startsWith("set")) {
      return null;
    }
    if (method.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
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
    final PsiJavaToken sign = assignment.getOperationSign();
    if (!JavaTokenType.EQ.equals(sign.getTokenType())) {
      return null;
    }
    final PsiExpression lhs = assignment.getLExpression();
    if (!(lhs instanceof PsiReferenceExpression)) {
      return null;
    }
    final PsiReferenceExpression reference = (PsiReferenceExpression)lhs;
    final PsiExpression qualifier = reference.getQualifierExpression();
    if (qualifier != null && !(qualifier instanceof PsiThisExpression) && !(qualifier instanceof PsiSuperExpression)) {
      return null;
    }
    final PsiElement referent = reference.resolve();
    if (referent == null) {
      return null;
    }
    if (!(referent instanceof PsiField)) {
      return null;
    }
    final PsiField field = (PsiField)referent;
    final PsiClass fieldContainingClass = field.getContainingClass();
    final PsiClass methodContainingClass = method.getContainingClass();
    if (!InheritanceUtil.isInheritorOrSelf(methodContainingClass, fieldContainingClass, true)) {
      return null;
    }
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
    final PsiType fieldType = field.getType();
    final PsiType parameterType = ((PsiVariable)rReferent).getType();
    if (fieldType.equalsToText(parameterType.getCanonicalText())) {
      return field;
    }
    else {
      return null;
    }
  }

  public static boolean isSimpleSetter(PsiMethod method) {
    return getFieldOfSetter(method) != null;
  }
}
