// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.generation.JavaOverrideImplementMemberChooser;
import com.intellij.codeInsight.generation.OverrideImplementExploreUtil;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.generation.PsiMethodMember;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.featureStatistics.ProductivityFeatureNames;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiFile;
import com.intellij.psi.infos.CandidateInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
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
    return BaseIntentionAction.canModify(startElement);
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable final Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    final PsiElement myPsiElement = startElement;

    if (editor == null || !FileModificationService.getInstance().prepareFileForWrite(myPsiElement.getContainingFile())) return;
    if (myPsiElement instanceof PsiEnumConstant) {
      final boolean hasClassInitializer = ((PsiEnumConstant)myPsiElement).getInitializingClass() != null;
      final JavaOverrideImplementMemberChooser chooser = chooseMethodsToImplement(editor, startElement,
                                                                              ((PsiEnumConstant)myPsiElement).getContainingClass(),
                                                                              hasClassInitializer);
      if (chooser == null) return;

      final List<PsiMethodMember> selectedElements = chooser.getSelectedElements();
      if (selectedElements == null || selectedElements.isEmpty()) return;

      WriteCommandAction.writeCommandAction(project, file).run(() -> {
        final PsiClass psiClass = ((PsiEnumConstant)myPsiElement).getOrCreateInitializingClass();
        OverrideImplementUtil.overrideOrImplementMethodsInRightPlace(editor, psiClass, selectedElements, chooser.isCopyJavadoc(),
                                                                     chooser.isInsertOverrideAnnotation(), chooser.isGenerateJavadoc());
      });
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
  protected static JavaOverrideImplementMemberChooser chooseMethodsToImplement(Editor editor,
                                                                               PsiElement startElement,
                                                                               PsiClass aClass,
                                                                               boolean implemented) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed(ProductivityFeatureNames.CODEASSISTS_OVERRIDE_IMPLEMENT);

    final Collection<CandidateInfo> overrideImplement = OverrideImplementExploreUtil.getMapToOverrideImplement(aClass, true, implemented).values();
    return OverrideImplementUtil
      .showJavaOverrideImplementChooser(editor, startElement, true, overrideImplement, new ArrayList<>());
  }
}
