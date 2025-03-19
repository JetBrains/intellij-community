// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class PsiGenerationInfo<T extends PsiMember> extends GenerationInfoBase implements GenerationInfo {
  private SmartPsiElementPointer<T> myMember;
  private final boolean myMergeIfExists;
  private static final Logger LOG = Logger.getInstance(PsiGenerationInfo.class);

  public PsiGenerationInfo(final @NotNull T member) {
    this(member, true);
  }

  public PsiGenerationInfo(@NotNull T member, boolean mergeIfExists) {
    setMember(member);
    myMergeIfExists = mergeIfExists;
  }

  private void setMember(@NotNull T member) {
    myMember = SmartPointerManager.getInstance(member.getProject()).createSmartPsiElementPointer(member);
  }

  @Override
  public final T getPsiMember() {
    return myMember.getElement();
  }

  @Override
  public void insert(final @NotNull PsiClass aClass, @Nullable PsiElement anchor, boolean before) throws IncorrectOperationException {
    T member = Objects.requireNonNull(getPsiMember());
    final PsiMember existingMember;
    if (member instanceof PsiField) {
      existingMember = aClass.findFieldByName(member.getName(), false);
    }
    else if (member instanceof PsiMethod) {
      existingMember = aClass.findMethodBySignature((PsiMethod)member, false);
    }
    else {
      existingMember = null;
    }
    if (existingMember == null || !existingMember.isPhysical() || !myMergeIfExists) {
      PsiElement newMember = GenerateMembersUtil.insert(aClass, member, anchor, before);
      member = (T)JavaCodeStyleManager.getInstance(aClass.getProject()).shortenClassReferences(newMember);
      LOG.assertTrue(member.isValid(), member);
    }
    else {
      final PsiModifierList modifierList = member.getModifierList();
      final PsiModifierList existingModifierList = existingMember.getModifierList();
      if (modifierList != null && existingModifierList != null) {
        final PsiAnnotation[] psiAnnotations = modifierList.getAnnotations();
        PsiElement annoAnchor = existingModifierList.getAnnotations().length > 0 ? existingModifierList.getAnnotations()[0] : existingModifierList.getFirstChild();
        for (PsiAnnotation annotation : psiAnnotations) {
          final PsiAnnotation existingAnno = existingModifierList.findAnnotation(annotation.getQualifiedName());
          if (existingAnno != null) {
            annoAnchor = existingAnno.replace(annotation);
          }
          else {
            existingModifierList.addBefore(annotation, annoAnchor);
          }
        }
      }
      member = (T)existingMember;
      if (!member.isValid()) {
        LOG.error("invalid member: " + member +
                  " self modified list: " + modifierList +
                  " existing modified list: " + existingModifierList);
      }
    }
    setMember(member);
  }
}
