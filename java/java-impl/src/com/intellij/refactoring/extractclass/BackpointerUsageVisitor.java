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

/*
 * User: anna
 * Date: 01-Sep-2008
 */
package com.intellij.refactoring.extractclass;

import com.intellij.psi.*;

import java.util.List;

class BackpointerUsageVisitor extends JavaRecursiveElementWalkingVisitor {
  private PsiMember myCause = null;

  private final List<PsiField> myFields;
  private final List<PsiClass> myInnerClasses;
  private final List<PsiMethod> myMethods;
  private final PsiClass mySourceClass;
  private final boolean myCheckThisExpression;


  public BackpointerUsageVisitor(final List<PsiField> fields,
                          final List<PsiClass> innerClasses, final List<PsiMethod> methods, final PsiClass sourceClass) {
    this(fields, innerClasses, methods, sourceClass, true);
  }

  public BackpointerUsageVisitor(List<PsiField> fields, List<PsiClass> innerClasses, List<PsiMethod> methods, PsiClass sourceClass,
                                 final boolean checkThisExpression) {
    myFields = fields;
    myInnerClasses = innerClasses;
    myMethods = methods;
    mySourceClass = sourceClass;
    myCheckThisExpression = checkThisExpression;
  }

  public void visitElement(PsiElement element) {
    if (myCause != null) {
      return;
    }
    super.visitElement(element);
  }

  public void visitReferenceExpression(PsiReferenceExpression expression) {
    if (myCause != null) {
      return;
    }
    super.visitReferenceExpression(expression);
    final PsiExpression qualifier = expression.getQualifierExpression();

    final PsiElement referent = expression.resolve();
    if (!(referent instanceof PsiField)) {
      return;
    }
    final PsiField field = (PsiField)referent;
    if (myFields.contains(field) || myInnerClasses.contains(field.getContainingClass())) {
      return;
    }
    if (field.hasModifierProperty(PsiModifier.STATIC)) {
      return;
    }
    if (qualifier == null || (myCheckThisExpression && (qualifier instanceof PsiThisExpression || qualifier instanceof PsiSuperExpression))) {
      myCause = field;
    }
  }

  public void visitMethodCallExpression(PsiMethodCallExpression expression) {
    if (myCause != null) {
      return;
    }
    super.visitMethodCallExpression(expression);
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    final PsiMethod method = expression.resolveMethod();
    if (method == null) {
      return;
    }
    final PsiClass containingClass = method.getContainingClass();
    if (myMethods.contains(method) || myInnerClasses.contains(containingClass)) {
      return;
    }
    if (method.hasModifierProperty(PsiModifier.STATIC)) {
      return;
    }
    if (!containingClass.equals(mySourceClass)) {
      return;
    }
    final PsiExpression qualifier = methodExpression.getQualifierExpression();
    if (qualifier == null || (myCheckThisExpression && (qualifier instanceof PsiThisExpression || qualifier instanceof PsiSuperExpression))) {
      myCause = method;
    }
  }

  public boolean isBackpointerRequired() {
    return myCause != null;
  }

  public PsiMember getCause() {
    return myCause;
  }

  public boolean backpointerRequired() {
    for (PsiMethod method : myMethods) {
      method.accept(this);
      if (myCause != null) {
        return true;
      }
    }
    for (PsiField field : myFields) {
      field.accept(this);
      if (myCause != null) {
        return true;
      }
    }
    for (PsiClass innerClass : myInnerClasses) {
      innerClass.accept(this);
      if (myCause != null) {
        return true;
      }
    }
    return false;
  }

}