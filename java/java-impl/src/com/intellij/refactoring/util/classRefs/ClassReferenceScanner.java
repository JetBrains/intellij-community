package com.intellij.refactoring.util.classRefs;

import com.intellij.psi.*;

/**
 * @author dsl
 */
public abstract class ClassReferenceScanner {
  protected PsiClass myClass;
  private PsiReference[] myReferences = null;

  public abstract PsiReference[] findReferences();

  public ClassReferenceScanner(PsiClass aClass) {
    myClass = aClass;
  }

  public void processReferences(ClassReferenceVisitor visitor) {
    if(myReferences == null) {
      myReferences = findReferences();
    }

    for (int i = 0; i < myReferences.length; i++) {
      processUsage(myReferences[i].getElement(), visitor);
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
