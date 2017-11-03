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
package com.intellij.codeInspection;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public abstract class InspectionManagerBase extends InspectionManager {
  private final Project myProject;
  @NonNls protected String myCurrentProfileName;

  public InspectionManagerBase(Project project) {
    myProject = project;
  }

  @Override
  @NotNull
  public Project getProject() {
    return myProject;
  }

  @Override
  @NotNull
  public CommonProblemDescriptor createProblemDescriptor(@NotNull String descriptionTemplate, QuickFix... fixes) {
    return new CommonProblemDescriptorImpl(fixes, descriptionTemplate);
  }

  @NotNull
  @Override
  public ModuleProblemDescriptor createProblemDescriptor(@Nls @NotNull String descriptionTemplate, Module module, QuickFix... fixes) {
    return new ModuleProblemDescriptorImpl(fixes, descriptionTemplate, module);
  }

  @Override
  @NotNull
  public ProblemDescriptor createProblemDescriptor(@NotNull PsiElement psiElement,
                                                   @NotNull String descriptionTemplate,
                                                   LocalQuickFix fix,
                                                   @NotNull ProblemHighlightType highlightType,
                                                   boolean onTheFly) {
    LocalQuickFix[] quickFixes = fix != null ? new LocalQuickFix[]{fix} : null;
    return createProblemDescriptor(psiElement, descriptionTemplate, onTheFly, quickFixes, highlightType);
  }

  @Override
  @NotNull
  public ProblemDescriptor createProblemDescriptor(@NotNull PsiElement psiElement,
                                                   @NotNull String descriptionTemplate,
                                                   boolean onTheFly,
                                                   LocalQuickFix[] fixes,
                                                   @NotNull ProblemHighlightType highlightType) {
    return createProblemDescriptor(psiElement, descriptionTemplate, fixes, highlightType, onTheFly, false);
  }

  @Override
  @NotNull
  public ProblemDescriptor createProblemDescriptor(@NotNull PsiElement psiElement,
                                                   @NotNull String descriptionTemplate,
                                                   LocalQuickFix[] fixes,
                                                   @NotNull ProblemHighlightType highlightType,
                                                   boolean onTheFly,
                                                   boolean isAfterEndOfLine) {
    return new ProblemDescriptorBase(psiElement, psiElement, descriptionTemplate, fixes, highlightType, isAfterEndOfLine, null, highlightType != ProblemHighlightType.INFORMATION, onTheFly);
  }

  @Override
  @NotNull
  public ProblemDescriptor createProblemDescriptor(@NotNull PsiElement startElement,
                                                   @NotNull PsiElement endElement,
                                                   @NotNull String descriptionTemplate,
                                                   @NotNull ProblemHighlightType highlightType,
                                                   boolean onTheFly,
                                                   LocalQuickFix... fixes) {
    return new ProblemDescriptorBase(startElement, endElement, descriptionTemplate, fixes, highlightType, false, null, highlightType != ProblemHighlightType.INFORMATION, onTheFly);
  }

  @NotNull
  @Override
  public ProblemDescriptor createProblemDescriptor(@NotNull final PsiElement psiElement,
                                                   final TextRange rangeInElement,
                                                   @NotNull final String descriptionTemplate,
                                                   @NotNull final ProblemHighlightType highlightType,
                                                   boolean onTheFly,
                                                   final LocalQuickFix... fixes) {
    return new ProblemDescriptorBase(psiElement, psiElement, descriptionTemplate, fixes, highlightType, false, rangeInElement, highlightType != ProblemHighlightType.INFORMATION, onTheFly);
  }

  @NotNull
  @Override
  public ProblemDescriptor createProblemDescriptor(@NotNull PsiElement psiElement,
                                                   @NotNull String descriptionTemplate,
                                                   boolean showTooltip,
                                                   @NotNull ProblemHighlightType highlightType,
                                                   boolean onTheFly,
                                                   LocalQuickFix... fixes) {
    return new ProblemDescriptorBase(psiElement, psiElement, descriptionTemplate, fixes, highlightType, false, null, showTooltip, onTheFly);
  }

  @Override
  @Deprecated
  @NotNull
  public ProblemDescriptor createProblemDescriptor(@NotNull PsiElement psiElement,
                                                   @NotNull String descriptionTemplate,
                                                   LocalQuickFix fix,
                                                   @NotNull ProblemHighlightType highlightType) {
    LocalQuickFix[] quickFixes = fix != null ? new LocalQuickFix[]{fix} : null;
    return createProblemDescriptor(psiElement, descriptionTemplate, false, quickFixes, highlightType);
  }

  @Override
  @Deprecated
  @NotNull
  public ProblemDescriptor createProblemDescriptor(@NotNull PsiElement psiElement,
                                                   @NotNull String descriptionTemplate,
                                                   LocalQuickFix[] fixes,
                                                   @NotNull ProblemHighlightType highlightType) {
    return createProblemDescriptor(psiElement, descriptionTemplate, fixes, highlightType, false, false);
  }

  @Override
  @Deprecated
  @NotNull
  public ProblemDescriptor createProblemDescriptor(@NotNull PsiElement psiElement,
                                                   @NotNull String descriptionTemplate,
                                                   LocalQuickFix[] fixes,
                                                   @NotNull ProblemHighlightType highlightType,
                                                   boolean isAfterEndOfLine) {
    return createProblemDescriptor(psiElement, descriptionTemplate, fixes, highlightType, true, isAfterEndOfLine);
  }

  @Override
  @Deprecated
  @NotNull
  public ProblemDescriptor createProblemDescriptor(@NotNull PsiElement startElement,
                                                   @NotNull PsiElement endElement,
                                                   @NotNull String descriptionTemplate,
                                                   @NotNull ProblemHighlightType highlightType,
                                                   LocalQuickFix... fixes) {
    return createProblemDescriptor(startElement, endElement, descriptionTemplate, highlightType, true, fixes);
  }

  @NotNull
  @Override
  @Deprecated
  public ProblemDescriptor createProblemDescriptor(@NotNull final PsiElement psiElement,
                                                   final TextRange rangeInElement,
                                                   @NotNull final String descriptionTemplate,
                                                   @NotNull final ProblemHighlightType highlightType,
                                                   final LocalQuickFix... fixes) {
    return createProblemDescriptor(psiElement, rangeInElement, descriptionTemplate, highlightType, true, fixes);
  }

  @NotNull
  @Deprecated
  @Override
  public ProblemDescriptor createProblemDescriptor(@NotNull PsiElement psiElement,
                                                   @NotNull String descriptionTemplate,
                                                   boolean showTooltip,
                                                   @NotNull ProblemHighlightType highlightType,
                                                   LocalQuickFix... fixes) {
    return createProblemDescriptor(psiElement, descriptionTemplate, showTooltip, highlightType, true, fixes);
  }

  public String getCurrentProfile() {
    if (myCurrentProfileName == null) {
      myCurrentProfileName = ProjectInspectionProfileManager.getInstance(getProject()).getProjectProfile();
      if (myCurrentProfileName == null) {
        myCurrentProfileName = InspectionProfileManager.getInstance().getCurrentProfile().getName();
      }
    }
    return myCurrentProfileName;
  }
}
