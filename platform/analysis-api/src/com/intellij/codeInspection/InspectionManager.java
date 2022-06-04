// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class InspectionManager {
  public static final ExtensionPointName<Condition<PsiElement>> CANT_BE_STATIC_EXTENSION = ExtensionPointName.create("com.intellij.cantBeStatic");

  public static InspectionManager getInstance(Project project) {
    return project.getService(InspectionManager.class);
  }

  @NotNull
  public abstract Project getProject();

  @NotNull
  @Contract(pure = true)
  public abstract CommonProblemDescriptor createProblemDescriptor(@NotNull @InspectionMessage String descriptionTemplate,
                                                                  QuickFix @Nullable ... fixes);

  @NotNull
  @Contract(pure = true)
  public abstract ModuleProblemDescriptor createProblemDescriptor(@NotNull @InspectionMessage String descriptionTemplate,
                                                                  @NotNull Module module,
                                                                  QuickFix @Nullable ... fixes);

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
                                                            @NotNull @InspectionMessage String descriptionTemplate,
                                                            @Nullable LocalQuickFix fix,
                                                            @NotNull ProblemHighlightType highlightType,
                                                            boolean onTheFly);

  @NotNull
  @Contract(pure = true)
  public abstract ProblemDescriptor createProblemDescriptor(@NotNull PsiElement psiElement,
                                                            @NotNull @InspectionMessage String descriptionTemplate,
                                                            boolean onTheFly,
                                                            LocalQuickFix[] fixes,
                                                            @NotNull ProblemHighlightType highlightType);

  @NotNull
  @Contract(pure = true)
  public abstract ProblemDescriptor createProblemDescriptor(@NotNull PsiElement psiElement,
                                                            @NotNull @InspectionMessage String descriptionTemplate,
                                                            LocalQuickFix @Nullable [] fixes,
                                                            @NotNull ProblemHighlightType highlightType,
                                                            boolean onTheFly,
                                                            boolean isAfterEndOfLine);

  @NotNull
  @Contract(pure = true)
  public abstract ProblemDescriptor createProblemDescriptor(@NotNull PsiElement startElement,
                                                            @NotNull PsiElement endElement,
                                                            @NotNull @InspectionMessage String descriptionTemplate,
                                                            @NotNull ProblemHighlightType highlightType,
                                                            boolean onTheFly,
                                                            LocalQuickFix... fixes);

  @NotNull
  @Contract(pure = true)
  public abstract ProblemDescriptor createProblemDescriptor(@NotNull final PsiElement psiElement,
                                                            @Nullable("null means the text range of the element") TextRange rangeInElement,
                                                            @NotNull @InspectionMessage String descriptionTemplate,
                                                            @NotNull ProblemHighlightType highlightType,
                                                            boolean onTheFly,
                                                            LocalQuickFix... fixes);

  @NotNull
  @Contract(pure = true)
  public abstract ProblemDescriptor createProblemDescriptor(@NotNull final PsiElement psiElement,
                                                            @NotNull @InspectionMessage String descriptionTemplate,
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
                                                            @NotNull @InspectionMessage String descriptionTemplate,
                                                            @Nullable LocalQuickFix fix,
                                                            @NotNull ProblemHighlightType highlightType);

  /**
   * @deprecated use {@link #createProblemDescriptor(PsiElement, String, boolean, LocalQuickFix[], ProblemHighlightType)} instead
   */
  @Deprecated(forRemoval = true)
  @NotNull
  @Contract(pure = true)
  public abstract ProblemDescriptor createProblemDescriptor(@NotNull PsiElement psiElement,
                                                            @NotNull @InspectionMessage String descriptionTemplate,
                                                            LocalQuickFix[] fixes,
                                                            @NotNull ProblemHighlightType highlightType);

  /**
   * @deprecated use {@link #createProblemDescriptor(PsiElement, String, LocalQuickFix[], ProblemHighlightType, boolean, boolean)} instead
   */
  @Deprecated(forRemoval = true)
  @NotNull
  @Contract(pure = true)
  public abstract ProblemDescriptor createProblemDescriptor(@NotNull PsiElement psiElement,
                                                            @NotNull @InspectionMessage String descriptionTemplate,
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
                                                            @NotNull @InspectionMessage String descriptionTemplate,
                                                            @NotNull ProblemHighlightType highlightType,
                                                            LocalQuickFix... fixes);


  /**
   * @deprecated use {@link #createProblemDescriptor(PsiElement, TextRange, String, ProblemHighlightType, boolean, LocalQuickFix...)} instead
   */
  @Deprecated(forRemoval = true)
  @NotNull
  @Contract(pure = true)
  public abstract ProblemDescriptor createProblemDescriptor(@NotNull final PsiElement psiElement,
                                                            final TextRange rangeInElement,
                                                            @NotNull @InspectionMessage String descriptionTemplate,
                                                            @NotNull ProblemHighlightType highlightType,
                                                            final LocalQuickFix... fixes);

  /**
   * @deprecated use {@link #createNewGlobalContext()} instead
   */
  @Deprecated(forRemoval = true)
  @NotNull
  @Contract(pure = true)
  public abstract GlobalInspectionContext createNewGlobalContext(boolean reuse);

  @NotNull
  @Contract(pure = true)
  public abstract GlobalInspectionContext createNewGlobalContext();

  @NotNull
  public abstract List<ProblemDescriptor> defaultProcessFile(@NotNull LocalInspectionTool tool, @NotNull PsiFile file);
}