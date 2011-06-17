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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.generation.PsiMethodMember;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.featureStatistics.ProductivityFeatureNames;
import com.intellij.ide.util.MemberChooser;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.MethodSignature;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.TreeMap;

public class ImplementMethodsFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  public ImplementMethodsFix(PsiElement aClass) {
    super(aClass);
  }

  @NotNull
  @Override
  public String getText() {
    return QuickFixBundle.message("implement.methods.fix");
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("implement.methods.fix");
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    PsiElement myPsiElement = startElement;
    return myPsiElement.isValid() && myPsiElement.getManager().isInProject(myPsiElement);
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable("is null when called from inspection") final Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    final PsiElement myPsiElement = startElement;

    if (editor == null || !CodeInsightUtilBase.prepareFileForWrite(myPsiElement.getContainingFile())) return;
    if (myPsiElement instanceof PsiEnumConstant) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed(ProductivityFeatureNames.CODEASSISTS_OVERRIDE_IMPLEMENT);
      final TreeMap<MethodSignature, CandidateInfo> result =
        new TreeMap<MethodSignature, CandidateInfo>(new OverrideImplementUtil.MethodSignatureComparator());
      final HashMap<MethodSignature, PsiMethod> abstracts = new HashMap<MethodSignature, PsiMethod>();
      for (PsiMethod method : ((PsiEnumConstant)myPsiElement).getContainingClass().getMethods()) {
        if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
          abstracts.put(method.getHierarchicalMethodSignature(), method);
        }
      }
      final HashMap<MethodSignature, PsiMethod> finals = new HashMap<MethodSignature, PsiMethod>();
      final HashMap<MethodSignature, PsiMethod> concretes = new HashMap<MethodSignature, PsiMethod>();
      OverrideImplementUtil.collectMethodsToImplement(null, abstracts, finals, concretes, result);

      final MemberChooser<PsiMethodMember> chooser =
        OverrideImplementUtil.showOverrideImplementChooser(editor, myPsiElement, true, result.values(), Collections.<CandidateInfo>emptyList());
      if (chooser == null) return;

      final List<PsiMethodMember> selectedElements = chooser.getSelectedElements();
      if (selectedElements == null || selectedElements.isEmpty()) return;

      new WriteCommandAction(project, file) {
        protected void run(final Result result) throws Throwable {
          final PsiClass psiClass = ((PsiEnumConstant)myPsiElement).getOrCreateInitializingClass();
          OverrideImplementUtil.overrideOrImplementMethodsInRightPlace(editor, psiClass, selectedElements, chooser.isCopyJavadoc(),
                                                                       chooser.isInsertOverrideAnnotation());
        }
      }.execute();
    }
    else {
      OverrideImplementUtil.chooseAndImplementMethods(project, editor, (PsiClass)myPsiElement);
    }

  }

  public boolean startInWriteAction() {
    return false;
  }

}
