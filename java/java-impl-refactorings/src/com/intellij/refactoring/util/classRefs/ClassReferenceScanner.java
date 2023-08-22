// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.util.classRefs;

import com.intellij.psi.*;

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

  private static void processUsage(PsiElement ref, ClassReferenceVisitor visitor) {
    if (ref instanceof PsiReferenceExpression refExpr){
      visitor.visitReferenceExpression(refExpr);
      return;
    }

    PsiElement parent = ref.getParent();
    if (parent instanceof PsiTypeElement typeElement){
      PsiElement pparent = parent.getParent();
      while(pparent instanceof PsiTypeElement){
        parent = pparent;
        pparent = parent.getParent();
      }
      var occurence = new ClassReferenceVisitor.TypeOccurence(ref, typeElement.getType());

      if (pparent instanceof PsiLocalVariable var){
        visitor.visitLocalVariableDeclaration(var, occurence);
      }
      else if(pparent instanceof PsiParameter parameter) {
        visitor.visitParameterDeclaration(parameter, occurence);
      }
      else if(pparent instanceof PsiField field) {
        visitor.visitFieldDeclaration(field, occurence);
      }
      else if (pparent instanceof PsiMethod method){
        visitor.visitMethodReturnType(method, occurence);
      }
      else if (pparent instanceof PsiTypeCastExpression cast){
        visitor.visitTypeCastExpression(cast, occurence);
      }
    }
    else if (parent instanceof PsiNewExpression newExpression){
      visitor.visitNewExpression(newExpression, new ClassReferenceVisitor.TypeOccurence(ref, newExpression.getType()));
    }
    else{
      visitor.visitOther(ref);
    }
  }
}
