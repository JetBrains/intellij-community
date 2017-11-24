/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.generation.OverrideImplementExploreUtil;
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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiFile;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public class ImplementMethodsFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  public ImplementMethodsFix(PsiElement aClass) {
    super(aClass);
  }

  @NotNull
  @Override
  public String getText() {
    return QuickFixBundle.message("implement.methods.fix");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    return startElement.getManager().isInProject(startElement);
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable("is null when called from inspection") final Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    final PsiElement myPsiElement = startElement;

    if (editor == null || !FileModificationService.getInstance().prepareFileForWrite(myPsiElement.getContainingFile())) return;
    if (myPsiElement instanceof PsiEnumConstant) {
      final boolean hasClassInitializer = ((PsiEnumConstant)myPsiElement).getInitializingClass() != null;
      final MemberChooser<PsiMethodMember> chooser = chooseMethodsToImplement(editor, startElement,
                                                                              ((PsiEnumConstant)myPsiElement).getContainingClass(), hasClassInitializer);
      if (chooser == null) return;

      final List<PsiMethodMember> selectedElements = chooser.getSelectedElements();
      if (selectedElements == null || selectedElements.isEmpty()) return;

      new WriteCommandAction(project, file) {
        @Override
        protected void run(@NotNull final Result result) throws Throwable {
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

  @Override
  public boolean startInWriteAction() {
    return false;
  }


  @Nullable
  protected static MemberChooser<PsiMethodMember> chooseMethodsToImplement(Editor editor,
                                                                           PsiElement startElement,
                                                                           PsiClass aClass, 
                                                                           boolean implemented) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed(ProductivityFeatureNames.CODEASSISTS_OVERRIDE_IMPLEMENT);

    final Collection<CandidateInfo> overrideImplement = OverrideImplementExploreUtil.getMapToOverrideImplement(aClass, true, implemented).values();
    return OverrideImplementUtil
      .showOverrideImplementChooser(editor, startElement, true, overrideImplement, ContainerUtil.newArrayList());
  }
}
