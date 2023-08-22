// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.util;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.psi.*;
import com.intellij.util.Processor;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public final class SpecialAnnotationsUtilBase {
  public static LocalQuickFix createAddToSpecialAnnotationsListQuickFix(@NotNull final @IntentionName String text,
                                                                        @NotNull final @IntentionFamilyName String family,
                                                                        @NotNull final @Nls String listTitle,
                                                                        @NotNull final List<String> targetList,
                                                                        @NotNull final String qualifiedName) {
    return new LocalQuickFix() {
      @Override
      @NotNull
      public String getName() {
        return text;
      }

      @Override
      @NotNull
      public String getFamilyName() {
        return family;
      }

      @Override
      public boolean startInWriteAction() {
        return false;
      }

      @Override
      public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
        doQuickFixInternal(project, targetList, qualifiedName);
      }

      @Override
      public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
        List<@NlsSafe String> prefixes = StreamEx.of(targetList).append(qualifiedName).sorted().toList();
        return IntentionPreviewInfo.addListOption(prefixes, qualifiedName, listTitle);
      }
    };
  }

  static void doQuickFixInternal(@NotNull Project project, @NotNull List<String> targetList, @NotNull String qualifiedName) {
    targetList.add(qualifiedName);
    Collections.sort(targetList);
    ProjectInspectionProfileManager.getInstance(project).fireProfileChanged();
  }

  public static void createAddToSpecialAnnotationFixes(@NotNull PsiModifierListOwner owner, @NotNull Processor<? super String> processor) {
    final PsiModifierList modifierList = owner.getModifierList();
    if (modifierList != null) {
      final PsiAnnotation[] psiAnnotations = modifierList.getAnnotations();
      for (PsiAnnotation psiAnnotation : psiAnnotations) {
        @NonNls final String name = psiAnnotation.getQualifiedName();
        if (name == null) continue;
        if (name.startsWith("java.") || //name.startsWith("javax.") ||
            name.startsWith("org.jetbrains.") ||
            NullableNotNullManager.isNullabilityAnnotation(psiAnnotation)) continue;
        if (!processor.process(name)) break;
      }
    }
  }
}
