// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public abstract class InspectionManager {
  public static final ExtensionPointName<Condition<PsiElement>> CANT_BE_STATIC_EXTENSION =
    ExtensionPointName.create("com.intellij.cantBeStatic");

  public static InspectionManager getInstance(Project project) {
    return ServiceManager.getService(project, InspectionManager.class);
  }

  @NotNull
  public abstract Project getProject();

  @NotNull
  @Contract(pure = true)
  public abstract CommonProblemDescriptor createProblemDescriptor(@NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String descriptionTemplate,
                                                                  @Nullable QuickFix... fixes);

  @NotNull
  @Contract(pure = true)
  public abstract ModuleProblemDescriptor createProblemDescriptor(@NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String descriptionTemplate,
                                                                  @NotNull Module module,
                                                                  @Nullable QuickFix... fixes);

  /**
   * Factory method for ProblemDescriptor. Should be called from LocalInspectionTool.checkXXX() methods.
   *
   * @param psiElement          problem is reported against
   * @param descriptionTemplate problem message. Use {@code #ref} for a link to problem piece of code and {@code #loc} for location in source code (see {@link CommonProblemDescriptor#getDescriptionTemplate()}).
   * @param fix                 should be null if no fix is provided.
   * @param onTheFly            for local tools on batch run
   */
  @NotNull
  @Contract(pure = true)
  public abstract ProblemDescriptor createProblemDescriptor(@NotNull PsiElement psiElement,
                                                            @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String descriptionTemplate,
                                                            @Nullable LocalQuickFix fix,
                                                            @NotNull ProblemHighlightType highlightType,
                                                            boolean onTheFly);

  @NotNull
  @Contract(pure = true)
  public abstract ProblemDescriptor createProblemDescriptor(@NotNull PsiElement psiElement,
                                                            @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String descriptionTemplate,
                                                            boolean onTheFly,
                                                            LocalQuickFix[] fixes,
                                                            @NotNull ProblemHighlightType highlightType);

  @NotNull
  @Contract(pure = true)
  public abstract ProblemDescriptor createProblemDescriptor(@NotNull PsiElement psiElement,
                                                            @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String descriptionTemplate,
                                                            @Nullable LocalQuickFix[] fixes,
                                                            @NotNull ProblemHighlightType highlightType,
                                                            boolean onTheFly,
                                                            boolean isAfterEndOfLine);

  @NotNull
  @Contract(pure = true)
  public abstract ProblemDescriptor createProblemDescriptor(@NotNull PsiElement startElement,
                                                            @NotNull PsiElement endElement,
                                                            @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String descriptionTemplate,
                                                            @NotNull ProblemHighlightType highlightType,
                                                            boolean onTheFly,
                                                            LocalQuickFix... fixes);

  @NotNull
  @Contract(pure = true)
  public abstract ProblemDescriptor createProblemDescriptor(@NotNull final PsiElement psiElement,
                                                            @Nullable("null means the text range of the element") TextRange rangeInElement,
                                                            @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String descriptionTemplate,
                                                            @NotNull ProblemHighlightType highlightType,
                                                            boolean onTheFly,
                                                            LocalQuickFix... fixes);

  @NotNull
  @Contract(pure = true)
  public abstract ProblemDescriptor createProblemDescriptor(@NotNull final PsiElement psiElement,
                                                            @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String descriptionTemplate,
                                                            final boolean showTooltip,
                                                            @NotNull ProblemHighlightType highlightType,
                                                            boolean onTheFly,
                                                            final LocalQuickFix... fixes);

  /**
   * @deprecated use {@link #createProblemDescriptor(PsiElement, String, boolean, LocalQuickFix[], ProblemHighlightType)} instead
   */
  @Deprecated
  @NotNull
  @Contract(pure = true)
  public abstract ProblemDescriptor createProblemDescriptor(@NotNull PsiElement psiElement,
                                                            @NotNull String descriptionTemplate,
                                                            @Nullable LocalQuickFix fix,
                                                            @NotNull ProblemHighlightType highlightType);

  /**
   * @deprecated use {@link #createProblemDescriptor(PsiElement, String, boolean, LocalQuickFix[], ProblemHighlightType)} instead
   */
  @Deprecated
  @NotNull
  @Contract(pure = true)
  public abstract ProblemDescriptor createProblemDescriptor(@NotNull PsiElement psiElement,
                                                            @NotNull String descriptionTemplate,
                                                            LocalQuickFix[] fixes,
                                                            @NotNull ProblemHighlightType highlightType);

  /**
   * @deprecated use {@link #createProblemDescriptor(PsiElement, String, LocalQuickFix[], ProblemHighlightType, boolean, boolean)} instead
   */
  @Deprecated
  @NotNull
  @Contract(pure = true)
  public abstract ProblemDescriptor createProblemDescriptor(@NotNull PsiElement psiElement,
                                                            @NotNull String descriptionTemplate,
                                                            LocalQuickFix[] fixes,
                                                            @NotNull ProblemHighlightType highlightType,
                                                            boolean isAfterEndOfLine);

  /**
   * @deprecated use {@link #createProblemDescriptor(PsiElement, PsiElement, String, ProblemHighlightType, boolean, LocalQuickFix...)} instead
   */
  @Deprecated
  @NotNull
  @Contract(pure = true)
  public abstract ProblemDescriptor createProblemDescriptor(@NotNull PsiElement startElement,
                                                            @NotNull PsiElement endElement,
                                                            @NotNull String descriptionTemplate,
                                                            @NotNull ProblemHighlightType highlightType,
                                                            LocalQuickFix... fixes);


  /**
   * @deprecated use {@link #createProblemDescriptor(PsiElement, TextRange, String, ProblemHighlightType, boolean, LocalQuickFix...)} instead
   */
  @Deprecated
  @NotNull
  @Contract(pure = true)
  public abstract ProblemDescriptor createProblemDescriptor(@NotNull final PsiElement psiElement,
                                                            final TextRange rangeInElement,
                                                            @NotNull final String descriptionTemplate,
                                                            @NotNull ProblemHighlightType highlightType,
                                                            final LocalQuickFix... fixes);

  /**
   * @deprecated use {@link #createNewGlobalContext()} instead
   */
  @Deprecated
  @NotNull
  @Contract(pure = true)
  public abstract GlobalInspectionContext createNewGlobalContext(boolean reuse);

  @NotNull
  @Contract(pure = true)
  public abstract GlobalInspectionContext createNewGlobalContext();
}