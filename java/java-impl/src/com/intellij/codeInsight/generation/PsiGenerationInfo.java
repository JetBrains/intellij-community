/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class PsiGenerationInfo<T extends PsiMember> extends GenerationInfo {
  private T myMember;
  private final boolean myMergeIfExists;
  private static final Logger LOG = Logger.getInstance("#" + PsiGenerationInfo.class.getName());

  public PsiGenerationInfo(@NotNull final T member) {
    myMember = member;
    myMergeIfExists = true;
  }

  public PsiGenerationInfo(@NotNull T member, boolean mergeIfExists) {
    myMember = member;
    myMergeIfExists = mergeIfExists;
  }

  public final T getPsiMember() {
    return myMember;
  }

  public void insert(final PsiClass aClass, PsiElement anchor, boolean before) throws IncorrectOperationException {
    final PsiMember existingMember;
    if (myMember instanceof PsiField) {
      existingMember = aClass.findFieldByName(myMember.getName(), false);
    }
    else if (myMember instanceof PsiMethod) {
      existingMember = aClass.findMethodBySignature((PsiMethod)myMember, false);
    }
    else {
      existingMember = null;
    }
    if (existingMember == null || !myMergeIfExists) {
      PsiElement newMember = GenerateMembersUtil.insert(aClass, myMember, anchor, before);
      myMember = (T)JavaCodeStyleManager.getInstance(aClass.getProject()).shortenClassReferences(newMember);
      LOG.assertTrue(myMember.isValid(), myMember);
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
      if (!myMember.isValid()) {
        LOG.error("invalid member: " + myMember +
                  " existing member: " + existingMember.isValid() +
                  " self modified list: " + modifierList +
                  " existing modified list: " + existingModifierList);
      }
    }

    if (myMember instanceof PsiMethod) {
      final Project project = myMember.getProject();
      SmartPsiElementPointer<T> pointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(myMember);
      PostprocessReformattingAspect reformattingAspect = project.getComponent(PostprocessReformattingAspect.class);
      reformattingAspect.doPostponedFormatting(myMember.getContainingFile().getViewProvider());
      myMember = pointer.getElement();
      if (myMember != null) {
        final PsiParameterList parameterList = ((PsiMethod)myMember).getParameterList();
        reformattingAspect.disablePostprocessFormattingInside(new Runnable(){
          public void run() {
            CodeStyleManager.getInstance(project).reformat(parameterList);
          }
        });
      }
    }
  }
}
