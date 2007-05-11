/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.generation;

import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class PsiGenerationInfo<T extends PsiMember> implements GenerationInfo {
  private T myMember;

  public PsiGenerationInfo(@NotNull final T member) {
    myMember = member;
  }

  @NotNull
  public final T getPsiMember() {
    return myMember;
  }

  public void insert(PsiClass aClass, PsiElement anchor, boolean before) throws IncorrectOperationException {
    final PsiMember existingMember;
    if (myMember instanceof PsiField) {
      existingMember = aClass.findFieldByName(myMember.getName(), false);
    }
    else if (myMember instanceof PsiMethod) {
      existingMember = aClass.findMethodBySignature((PsiMethod)myMember, false);
    }
    else existingMember = null;
    if (existingMember == null) {
      PsiElement newMember = GenerateMembersUtil.insert(aClass, myMember, anchor, before);
      myMember = (T)CodeStyleManager.getInstance(aClass.getProject()).shortenClassReferences(newMember);
    }
    else {
      final PsiModifierList modifierList = myMember.getModifierList();
      final PsiModifierList existingModifierList = existingMember.getModifierList();
      if (modifierList != null && existingModifierList != null) {
        final PsiAnnotation[] psiAnnotations = modifierList.getAnnotations();
        PsiElement annoAnchor = existingModifierList.getAnnotations().length > 0 ? existingModifierList.getAnnotations()[0] : existingModifierList.getFirstChild();
        if (psiAnnotations.length > 0) {
          for (PsiAnnotation annotation : psiAnnotations) {
            final PsiAnnotation existingAnno = existingModifierList.findAnnotation(annotation.getQualifiedName());
            if (existingAnno != null) existingAnno.replace(annotation);
            else existingModifierList.addBefore(annotation, annoAnchor);
          }
        }
      }
      myMember = (T)existingMember;
    }
  }
}
