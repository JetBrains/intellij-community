/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
public abstract class ClassReferenceScanner {
  protected PsiClass myClass;
  private PsiReference[] myReferences;

  public abstract PsiReference[] findReferences();

  public ClassReferenceScanner(PsiClass aClass) {
    myClass = aClass;
  }

  public void processReferences(ClassReferenceVisitor visitor) {
    if(myReferences == null) {
      myReferences = findReferences();
    }

    for (PsiReference reference : myReferences) {
      processUsage(reference.getElement(), visitor);
    }
  }

  private void processUsage(PsiElement ref, ClassReferenceVisitor visitor) {
    if (ref instanceof PsiReferenceExpression){
      visitor.visitReferenceExpression((PsiReferenceExpression) ref);
      return;
    }

    PsiElement parent = ref.getParent();
    if (parent instanceof PsiTypeElement){
      PsiElement pparent = parent.getParent();
      while(pparent instanceof PsiTypeElement){
        parent = pparent;
        pparent = parent.getParent();
      }
      ClassReferenceVisitor.TypeOccurence occurence =
              new ClassReferenceVisitor.TypeOccurence(ref, ((PsiTypeElement) parent).getType());


      if (pparent instanceof PsiLocalVariable){
        visitor.visitLocalVariableDeclaration((PsiLocalVariable) pparent, occurence);
      }
      else if(pparent instanceof PsiParameter) {
        PsiParameter parameter = (PsiParameter) pparent;
        visitor.visitParameterDeclaration(parameter, occurence);
      }
      else if(pparent instanceof PsiField) {
        visitor.visitFieldDeclaration((PsiField) pparent, occurence);
      }
      else if (pparent instanceof PsiMethod){
        visitor.visitMethodReturnType((PsiMethod) pparent, occurence);
      }
      else if (pparent instanceof PsiTypeCastExpression){
        visitor.visitTypeCastExpression((PsiTypeCastExpression) pparent, occurence);
      }
    }
    else if (parent instanceof PsiNewExpression){
      visitor.visitNewExpression((PsiNewExpression) parent,
              new ClassReferenceVisitor.TypeOccurence(ref, ((PsiNewExpression) parent).getType())
      );
    }
    else{
      visitor.visitOther(ref);
    }
  }
}
