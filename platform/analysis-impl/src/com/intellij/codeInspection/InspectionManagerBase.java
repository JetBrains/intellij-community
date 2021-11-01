// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.PairProcessor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public abstract class InspectionManagerBase extends InspectionManager {
  private final Project myProject;

  protected String myCurrentProfileName;

  public InspectionManagerBase(Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public Project getProject() {
    return myProject;
  }

  @NotNull
  @Override
  public CommonProblemDescriptor createProblemDescriptor(@InspectionMessage @NotNull String descriptionTemplate, QuickFix... fixes) {
    return new CommonProblemDescriptorImpl(fixes, descriptionTemplate);
  }

  @NotNull
  @Override
  public ModuleProblemDescriptor createProblemDescriptor(@InspectionMessage @NotNull String descriptionTemplate, @NotNull Module module, QuickFix... fixes) {
    return new ModuleProblemDescriptorImpl(fixes, descriptionTemplate, module);
  }

  @NotNull
  @Override
  public ProblemDescriptor createProblemDescriptor(@NotNull PsiElement psiElement,
                                                   @NotNull String descriptionTemplate,
                                                   LocalQuickFix fix,
                                                   @NotNull ProblemHighlightType highlightType,
                                                   boolean onTheFly) {
    LocalQuickFix[] quickFixes = fix != null ? new LocalQuickFix[]{fix} : null;
    return createProblemDescriptor(psiElement, descriptionTemplate, onTheFly, quickFixes, highlightType);
  }

  @NotNull
  @Override
  public ProblemDescriptor createProblemDescriptor(@NotNull PsiElement psiElement,
                                                   @NotNull String descriptionTemplate,
                                                   boolean onTheFly,
                                                   LocalQuickFix[] fixes,
                                                   @NotNull ProblemHighlightType highlightType) {
    return createProblemDescriptor(psiElement, descriptionTemplate, fixes, highlightType, onTheFly, false);
  }

  @NotNull
  @Override
  public ProblemDescriptor createProblemDescriptor(@NotNull PsiElement psiElement,
                                                   @NotNull @InspectionMessage String descriptionTemplate,
                                                   LocalQuickFix @Nullable [] fixes,
                                                   @NotNull ProblemHighlightType highlightType,
                                                   boolean onTheFly,
                                                   boolean isAfterEndOfLine) {
    boolean tooltip = highlightType != ProblemHighlightType.INFORMATION;
    return new ProblemDescriptorBase(psiElement, psiElement, descriptionTemplate, fixes, highlightType, isAfterEndOfLine, null, tooltip, onTheFly);
  }

  @NotNull
  @Override
  public ProblemDescriptor createProblemDescriptor(@NotNull PsiElement startElement,
                                                   @NotNull PsiElement endElement,
                                                   @NotNull @InspectionMessage String descriptionTemplate,
                                                   @NotNull ProblemHighlightType highlightType,
                                                   boolean onTheFly,
                                                   LocalQuickFix... fixes) {
    boolean tooltip = highlightType != ProblemHighlightType.INFORMATION;
    return new ProblemDescriptorBase(startElement, endElement, descriptionTemplate, fixes, highlightType, false, null, tooltip, onTheFly);
  }

  @NotNull
  @Override
  public ProblemDescriptor createProblemDescriptor(@NotNull final PsiElement psiElement,
                                                   final TextRange rangeInElement,
                                                   @NotNull @InspectionMessage String descriptionTemplate,
                                                   @NotNull final ProblemHighlightType highlightType,
                                                   boolean onTheFly,
                                                   final LocalQuickFix... fixes) {
    boolean tooltip = highlightType != ProblemHighlightType.INFORMATION;
    return new ProblemDescriptorBase(psiElement, psiElement, descriptionTemplate, fixes, highlightType, false, rangeInElement, tooltip, onTheFly);
  }

  @NotNull
  @Override
  public ProblemDescriptor createProblemDescriptor(@NotNull PsiElement psiElement,
                                                   @NotNull @InspectionMessage String descriptionTemplate,
                                                   boolean showTooltip,
                                                   @NotNull ProblemHighlightType highlightType,
                                                   boolean onTheFly,
                                                   LocalQuickFix... fixes) {
    return new ProblemDescriptorBase(psiElement, psiElement, descriptionTemplate, fixes, highlightType, false, null, showTooltip, onTheFly);
  }

  @NotNull
  @Override
  @Deprecated
  public ProblemDescriptor createProblemDescriptor(@NotNull PsiElement psiElement,
                                                   @NotNull @InspectionMessage String descriptionTemplate,
                                                   LocalQuickFix fix,
                                                   @NotNull ProblemHighlightType highlightType) {
    LocalQuickFix[] quickFixes = fix != null ? new LocalQuickFix[]{fix} : null;
    return createProblemDescriptor(psiElement, descriptionTemplate, false, quickFixes, highlightType);
  }

  @NotNull
  @Override
  @Deprecated
  public ProblemDescriptor createProblemDescriptor(@NotNull PsiElement psiElement,
                                                   @NotNull @InspectionMessage String descriptionTemplate,
                                                   LocalQuickFix[] fixes,
                                                   @NotNull ProblemHighlightType highlightType) {
    return createProblemDescriptor(psiElement, descriptionTemplate, fixes, highlightType, false, false);
  }

  @NotNull
  @Override
  @Deprecated
  public ProblemDescriptor createProblemDescriptor(@NotNull PsiElement psiElement,
                                                   @NotNull @InspectionMessage String descriptionTemplate,
                                                   LocalQuickFix[] fixes,
                                                   @NotNull ProblemHighlightType highlightType,
                                                   boolean isAfterEndOfLine) {
    return createProblemDescriptor(psiElement, descriptionTemplate, fixes, highlightType, true, isAfterEndOfLine);
  }

  @NotNull
  @Override
  @Deprecated
  public ProblemDescriptor createProblemDescriptor(@NotNull PsiElement startElement,
                                                   @NotNull PsiElement endElement,
                                                   @NotNull @InspectionMessage String descriptionTemplate,
                                                   @NotNull ProblemHighlightType highlightType,
                                                   LocalQuickFix... fixes) {
    return createProblemDescriptor(startElement, endElement, descriptionTemplate, highlightType, true, fixes);
  }

  @NotNull
  @Override
  @Deprecated
  public ProblemDescriptor createProblemDescriptor(@NotNull final PsiElement psiElement,
                                                   final TextRange rangeInElement,
                                                   @NotNull @InspectionMessage String descriptionTemplate,
                                                   @NotNull final ProblemHighlightType highlightType,
                                                   final LocalQuickFix... fixes) {
    return createProblemDescriptor(psiElement, rangeInElement, descriptionTemplate, highlightType, true, fixes);
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

  public void setProfile(@NotNull String name) {
    myCurrentProfileName = name;
  }

  @Override
  @NotNull
  public List<ProblemDescriptor> defaultProcessFile(@NotNull LocalInspectionTool tool, @NotNull PsiFile file) {
    ProgressIndicator indicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
    if (indicator == null) {
      throw new IllegalStateException("Inspections must be run under progress indicator. See ProgressManager.run*() or .execute*()");
    }
    Map<LocalInspectionToolWrapper, List<ProblemDescriptor>> map =
      InspectionEngine.inspectEx(Collections.singletonList(new LocalInspectionToolWrapper(tool)), file, file.getTextRange(),
                                 file.getTextRange(), false,
                                 false, true, indicator, PairProcessor.alwaysTrue());
    return ContainerUtil.flatten(map.values());
  }
}
