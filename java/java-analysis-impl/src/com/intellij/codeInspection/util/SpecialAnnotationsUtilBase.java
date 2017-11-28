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
package com.intellij.codeInspection.util;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class SpecialAnnotationsUtilBase {
  public static LocalQuickFix createAddToSpecialAnnotationsListQuickFix(@NotNull final String text,
                                                                        @NotNull final String family,
                                                                        @NotNull final List<String> targetList,
                                                                        @NotNull final String qualifiedName,
                                                                        final PsiElement context) {
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
    };
  }

  static void doQuickFixInternal(@NotNull Project project, @NotNull List<String> targetList, @NotNull String qualifiedName) {
    targetList.add(qualifiedName);
    Collections.sort(targetList);
    //correct save settings

    //TODO lesya
    ProjectInspectionProfileManager.getInstance(project).fireProfileChanged();
    /*
    try {
      inspectionProfile.save();
    }
    catch (IOException e) {
      Messages.showErrorDialog(project, e.getMessage(), CommonBundle.getErrorTitle());
    }

    */
  }

  public static void createAddToSpecialAnnotationFixes(@NotNull PsiModifierListOwner owner, @NotNull Processor<String> processor) {
    final PsiModifierList modifierList = owner.getModifierList();
    if (modifierList != null) {
      final PsiAnnotation[] psiAnnotations = modifierList.getAnnotations();
      for (PsiAnnotation psiAnnotation : psiAnnotations) {
        @NonNls final String name = psiAnnotation.getQualifiedName();
        if (name == null) continue;
        if (name.startsWith("java.") || name.startsWith("javax.") ||
            name.startsWith("org.jetbrains.") && AnnotationUtil.isJetbrainsAnnotation(StringUtil.getShortName(name))) continue;
        if (!processor.process(name)) break;
      }
    }
  }
}
