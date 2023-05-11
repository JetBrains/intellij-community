// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.generation.*;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.featureStatistics.ProductivityFeatureNames;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
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
        OverrideImplementUtil.overrideOrImplementMethodsInRightPlace(editor, psiClass, selectedElements, chooser.getOptions());
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

    final Collection<CandidateInfo> overrideImplement =
      OverrideImplementExploreUtil.getMapToOverrideImplement(aClass, true, implemented).values();
    return OverrideImplementUtil
      .showJavaOverrideImplementChooser(editor, startElement, true, overrideImplement, new ArrayList<>());
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    final PsiElement startElement = getStartElement();
    final PsiElement copy = PsiTreeUtil.findSameElementInCopy(startElement, file);
    final OverrideOrImplementOptions options = new OverrideOrImplementOptions() {
      @Override
      public boolean isInsertOverrideWherePossible() {
        return true;
      }
    };
    final Collection<CandidateInfo> overrideImplement;
    final PsiClass aClass;
    if (copy instanceof PsiEnumConstant enumConstant) {
      final PsiClass containingClass = enumConstant.getContainingClass();
      if (containingClass == null) return IntentionPreviewInfo.EMPTY;
      aClass = enumConstant.getOrCreateInitializingClass();
      overrideImplement = OverrideImplementExploreUtil.getMapToOverrideImplement(containingClass, true, false).values();
    }
    else {
      if (!(copy instanceof PsiClass psiClass)) return IntentionPreviewInfo.EMPTY;
      aClass = psiClass;
      overrideImplement = OverrideImplementExploreUtil.getMethodsToOverrideImplement(psiClass, true);
    }
    final List<PsiMethodMember> members = filterNonDefaultMethodMembers(overrideImplement);
    OverrideImplementUtil.overrideOrImplementMethodsInRightPlace(editor, aClass, members, options);
    return IntentionPreviewInfo.DIFF;
  }

  @NotNull
  public static List<PsiMethodMember> filterNonDefaultMethodMembers(Collection<CandidateInfo> overrideImplement) {
    return ContainerUtil.map(
      ContainerUtil.filter(overrideImplement,
                           t -> t.getElement() instanceof PsiMethod method && !method.hasModifierProperty(PsiModifier.DEFAULT)),
      PsiMethodMember::new
    );
  }
}
