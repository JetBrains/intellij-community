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
package com.intellij.codeInspection;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public abstract class InspectionManager {
  public static final String INSPECTION_GROUP_ID = "Inspections";  // for use in notifications

  public static InspectionManager getInstance(Project project) {
    return ServiceManager.getService(project, InspectionManager.class);
  }

  @NotNull public abstract CommonProblemDescriptor createProblemDescriptor(@NotNull String descriptionTemplate, QuickFix... fixes);

  /**
   * Factory method for ProblemDescriptor. Should be called from LocalInspectionTool.checkXXX() methods.
   * @param psiElement problem is reported against
   * @param descriptionTemplate problem message. Use <code>#ref</code> for a link to problem piece of code and <code>#loc</code> for location in source code.
   * @param fix should be null if no fix is provided.
   * @param onTheFly for local tools on batch run
   */
  @NotNull public abstract ProblemDescriptor createProblemDescriptor(@NotNull PsiElement psiElement, @NotNull String descriptionTemplate,
                                                                     LocalQuickFix fix, ProblemHighlightType highlightType, boolean onTheFly);

  @NotNull public abstract ProblemDescriptor createProblemDescriptor(@NotNull PsiElement psiElement, @NotNull String descriptionTemplate,
                                                                     boolean onTheFly, LocalQuickFix[] fixes, ProblemHighlightType highlightType);

  @NotNull public abstract ProblemDescriptor createProblemDescriptor(@NotNull PsiElement psiElement, @NotNull String descriptionTemplate,
                                                                     LocalQuickFix[] fixes, ProblemHighlightType highlightType,
                                                                     boolean onTheFly, boolean isAfterEndOfLine);

  @NotNull public abstract ProblemDescriptor createProblemDescriptor(@NotNull PsiElement startElement,
                                                                     @NotNull PsiElement endElement,
                                                                     @NotNull String descriptionTemplate,
                                                                     ProblemHighlightType highlightType, boolean onTheFly, LocalQuickFix... fixes);

  
  @NotNull public abstract Project getProject();

  public abstract ProblemDescriptor createProblemDescriptor(@NotNull final PsiElement psiElement,
                                                            final TextRange rangeInElement,
                                                            @NotNull final String descriptionTemplate,
                                                            final ProblemHighlightType highlightType, boolean onTheFly, final LocalQuickFix... fixes);

  public abstract ProblemDescriptor createProblemDescriptor(@NotNull final PsiElement psiElement,
                                                            @NotNull final String descriptionTemplate,
                                                            final ProblemHighlightType highlightType,
                                                            @Nullable final HintAction hintAction, boolean onTheFly, final LocalQuickFix... fixes);

  public abstract ProblemDescriptor createProblemDescriptor(@NotNull final PsiElement psiElement,
                                                            @NotNull final String descriptionTemplate,
                                                            final boolean showTooltip,
                                                            final ProblemHighlightType highlightType, boolean onTheFly, final LocalQuickFix... fixes);


  @Deprecated
  /**
   * Factory method for ProblemDescriptor. Should be called from LocalInspectionTool.checkXXX() methods.
   * @param psiElement problem is reported against
   * @param descriptionTemplate problem message. Use <code>#ref</code> for a link to problem piece of code and <code>#loc</code> for location in source code.
   * @param fix should be null if no fix is provided.
   */
  @NotNull public abstract ProblemDescriptor createProblemDescriptor(@NotNull PsiElement psiElement, @NotNull String descriptionTemplate, LocalQuickFix fix, ProblemHighlightType highlightType);

  @Deprecated
  @NotNull public abstract ProblemDescriptor createProblemDescriptor(@NotNull PsiElement psiElement, @NotNull String descriptionTemplate, LocalQuickFix[] fixes, ProblemHighlightType highlightType);

  @Deprecated
  @NotNull public abstract ProblemDescriptor createProblemDescriptor(@NotNull PsiElement psiElement, @NotNull String descriptionTemplate, LocalQuickFix[] fixes, ProblemHighlightType highlightType, boolean isAfterEndOfLine);

  @Deprecated
  @NotNull public abstract ProblemDescriptor createProblemDescriptor(@NotNull PsiElement startElement,
                                                                     @NotNull PsiElement endElement,
                                                                     @NotNull String descriptionTemplate,
                                                                     ProblemHighlightType highlightType,
                                                                     LocalQuickFix... fixes
  );


  @Deprecated
  public abstract ProblemDescriptor createProblemDescriptor(@NotNull final PsiElement psiElement,
                                                            final TextRange rangeInElement,
                                                            @NotNull final String descriptionTemplate,
                                                            final ProblemHighlightType highlightType,
                                                            final LocalQuickFix... fixes);

  @Deprecated
  public abstract ProblemDescriptor createProblemDescriptor(@NotNull final PsiElement psiElement,
                                                            @NotNull final String descriptionTemplate,
                                                            final ProblemHighlightType highlightType,
                                                            @Nullable final HintAction hintAction,
                                                            final LocalQuickFix... fixes);

  @Deprecated
  public abstract ProblemDescriptor createProblemDescriptor(@NotNull final PsiElement psiElement,
                                                            @NotNull final String descriptionTemplate,
                                                            final boolean showTooltip,
                                                            final ProblemHighlightType highlightType, final LocalQuickFix... fixes);
}
