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
package com.intellij.codeInsight.generation;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class PsiGenerationInfo<T extends PsiMember> extends GenerationInfoBase implements GenerationInfo {
  private SmartPsiElementPointer<T> myMember;
  private final boolean myMergeIfExists;
  private static final Logger LOG = Logger.getInstance(PsiGenerationInfo.class);

  public PsiGenerationInfo(@NotNull final T member) {
    this(member, true);
  }

  public PsiGenerationInfo(@NotNull T member, boolean mergeIfExists) {
    setMember(member);
    myMergeIfExists = mergeIfExists;
  }

  private void setMember(@NotNull T member) {
    myMember = SmartPointerManager.getInstance(member.getProject()).createSmartPsiElementPointer(member);
  }

  @NotNull
  @Override
  public final T getPsiMember() {
    return ObjectUtils.assertNotNull(myMember.getElement());
  }

  @Override
  public boolean isMemberValid() {
    return myMember.getElement() != null;
  }

  @Override
  public void insert(@NotNull final PsiClass aClass, @Nullable PsiElement anchor, boolean before) throws IncorrectOperationException {
    T member = getPsiMember();
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
      setMember(member);
    }
    else {
      final PsiModifierList modifierList = member.getModifierList();
      final PsiModifierList existingModifierList = existingMember.getModifierList();
      if (modifierList != null && existingModifierList != null) {
        final PsiAnnotation[] psiAnnotations = modifierList.getAnnotations();
        PsiElement annoAnchor = existingModifierList.getAnnotations().length > 0 ? existingModifierList.getAnnotations()[0] : existingModifierList.getFirstChild();
        if (psiAnnotations.length > 0) {
          for (PsiAnnotation annotation : psiAnnotations) {
            final PsiAnnotation existingAnno = existingModifierList.findAnnotation(annotation.getQualifiedName());
            if (existingAnno != null){
              annoAnchor = existingAnno.replace(annotation);
            }
            else {
              existingModifierList.addBefore(annotation, annoAnchor);
            }
          }
        }
      }
      member = (T)existingMember;
      if (!member.isValid()) {
        LOG.error("invalid member: " + member +
                  " existing member: " + existingMember.isValid() +
                  " self modified list: " + modifierList +
                  " existing modified list: " + existingModifierList);
      }
      setMember(member);
    }
  }
}
