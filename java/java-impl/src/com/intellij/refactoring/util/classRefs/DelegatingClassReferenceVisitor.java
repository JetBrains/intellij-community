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
package com.intellij.refactoring.util.classRefs;

import com.intellij.psi.*;

/**
 * @author dsl
 */
public class DelegatingClassReferenceVisitor implements ClassReferenceVisitor {
  private final ClassReferenceVisitor myDelegate;

  public DelegatingClassReferenceVisitor(ClassReferenceVisitor delegate) {

    myDelegate = delegate;
  }

  @Override
  public void visitReferenceExpression(PsiReferenceExpression referenceExpression) {
    myDelegate.visitReferenceExpression(referenceExpression);
  }

  @Override
  public void visitLocalVariableDeclaration(PsiLocalVariable variable, ClassReferenceVisitor.TypeOccurence occurence) {
    myDelegate.visitLocalVariableDeclaration(variable, occurence);
  }

  @Override
  public void visitFieldDeclaration(PsiField field, ClassReferenceVisitor.TypeOccurence occurence) {
    myDelegate.visitFieldDeclaration(field, occurence);
  }

  @Override
  public void visitParameterDeclaration(PsiParameter parameter, ClassReferenceVisitor.TypeOccurence occurence) {
    myDelegate.visitParameterDeclaration(parameter, occurence);
  }

  @Override
  public void visitMethodReturnType(PsiMethod method, ClassReferenceVisitor.TypeOccurence occurence) {
    myDelegate.visitMethodReturnType(method, occurence);
  }

  @Override
  public void visitTypeCastExpression(PsiTypeCastExpression typeCastExpression, ClassReferenceVisitor.TypeOccurence occurence) {
    myDelegate.visitTypeCastExpression(typeCastExpression, occurence);
  }

  @Override
  public void visitNewExpression(PsiNewExpression newExpression, ClassReferenceVisitor.TypeOccurence occurence) {
    myDelegate.visitNewExpression(newExpression, occurence);
  }

  @Override
  public void visitOther(PsiElement ref) {
    myDelegate.visitOther(ref);
  }

}
