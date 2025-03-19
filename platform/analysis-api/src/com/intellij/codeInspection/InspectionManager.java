// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.*;

import java.util.List;

/**
 * @see ProblemsHolder
 */
public abstract class InspectionManager {
  public static final ExtensionPointName<Condition<PsiElement>> CANT_BE_STATIC_EXTENSION =
    ExtensionPointName.create("com.intellij.cantBeStatic");

  public static InspectionManager getInstance(Project project) {
    return project.getService(InspectionManager.class);
  }

  public abstract @NotNull Project getProject();

  @Contract(pure = true)
  public abstract @NotNull CommonProblemDescriptor createProblemDescriptor(@NotNull @InspectionMessage String descriptionTemplate,
                                                                           @NotNull QuickFix<?> @Nullable ... fixes);

  @Contract(pure = true)
  public abstract @NotNull ModuleProblemDescriptor createProblemDescriptor(@NotNull @InspectionMessage String descriptionTemplate,
                                                                           @NotNull Module module,
                                                                           @NotNull QuickFix<?> @Nullable ... fixes);

  /**
   * Factory method for ProblemDescriptor. Should be called from LocalInspectionTool.checkXXX() methods.
   *
   * @param psiElement          problem is reported against
   * @param descriptionTemplate problem message. Use {@code #ref} for a link to problem piece of code and {@code #loc} for location in source code (see {@link CommonProblemDescriptor#getDescriptionTemplate()}).
   * @param fix                 should be null if no fix is provided.
   * @param onTheFly            for local tools on batch run
   */
  @Contract(pure = true)
  public abstract @NotNull ProblemDescriptor createProblemDescriptor(@NotNull PsiElement psiElement,
                                                                     @NotNull @InspectionMessage String descriptionTemplate,
                                                                     @Nullable LocalQuickFix fix,
                                                                     @NotNull ProblemHighlightType highlightType,
                                                                     boolean onTheFly);

  @Contract(pure = true)
  public abstract @NotNull ProblemDescriptor createProblemDescriptor(@NotNull PsiElement psiElement,
                                                                     @NotNull @InspectionMessage String descriptionTemplate,
                                                                     boolean onTheFly,
                                                                     @NotNull LocalQuickFix @Nullable [] fixes,
                                                                     @NotNull ProblemHighlightType highlightType);

  @Contract(pure = true)
  public abstract @NotNull ProblemDescriptor createProblemDescriptor(@NotNull PsiElement psiElement,
                                                                     @NotNull @InspectionMessage String descriptionTemplate,
                                                                     @NotNull LocalQuickFix @Nullable [] fixes,
                                                                     @NotNull ProblemHighlightType highlightType,
                                                                     boolean onTheFly,
                                                                     boolean isAfterEndOfLine);

  @Contract(pure = true)
  public abstract @NotNull ProblemDescriptor createProblemDescriptor(@NotNull PsiElement startElement,
                                                                     @NotNull PsiElement endElement,
                                                                     @NotNull @InspectionMessage String descriptionTemplate,
                                                                     @NotNull ProblemHighlightType highlightType,
                                                                     boolean onTheFly,
                                                                     @NotNull LocalQuickFix @Nullable ... fixes);

  @Contract(pure = true)
  public abstract @NotNull ProblemDescriptor createProblemDescriptor(@NotNull PsiElement psiElement,
                                                                     @Nullable("null means the text range of the element") TextRange rangeInElement,
                                                                     @NotNull @InspectionMessage String descriptionTemplate,
                                                                     @NotNull ProblemHighlightType highlightType,
                                                                     boolean onTheFly,
                                                                     @NotNull LocalQuickFix @Nullable ... fixes);

  @Contract(pure = true)
  public abstract @NotNull ProblemDescriptor createProblemDescriptor(@NotNull PsiElement psiElement,
                                                                     @NotNull @InspectionMessage String descriptionTemplate,
                                                                     boolean showTooltip,
                                                                     @NotNull ProblemHighlightType highlightType,
                                                                     boolean onTheFly,
                                                                     @NotNull LocalQuickFix @Nullable ... fixes);

  //<editor-fold desc="Deprecated stuff.">

  /**
   * @deprecated use {@link #createProblemDescriptor(PsiElement, String, boolean, LocalQuickFix[], ProblemHighlightType)} instead
   */
  @Deprecated
  @Contract(pure = true)
  public abstract @NotNull ProblemDescriptor createProblemDescriptor(@NotNull PsiElement psiElement,
                                                                     @NotNull @InspectionMessage String descriptionTemplate,
                                                                     @Nullable LocalQuickFix fix,
                                                                     @NotNull ProblemHighlightType highlightType);

  /**
   * @deprecated use {@link #createProblemDescriptor(PsiElement, String, boolean, LocalQuickFix[], ProblemHighlightType)} instead
   */
  @Deprecated(forRemoval = true)
  @Contract(pure = true)
  public abstract @NotNull ProblemDescriptor createProblemDescriptor(@NotNull PsiElement psiElement,
                                                                     @NotNull @InspectionMessage String descriptionTemplate,
                                                                     @NotNull LocalQuickFix @Nullable [] fixes,
                                                                     @NotNull ProblemHighlightType highlightType);

  /**
   * @deprecated use {@link #createProblemDescriptor(PsiElement, String, LocalQuickFix[], ProblemHighlightType, boolean, boolean)} instead
   */
  @Deprecated(forRemoval = true)
  @Contract(pure = true)
  public abstract @NotNull ProblemDescriptor createProblemDescriptor(@NotNull PsiElement psiElement,
                                                                     @NotNull @InspectionMessage String descriptionTemplate,
                                                                     @NotNull LocalQuickFix @Nullable [] fixes,
                                                                     @NotNull ProblemHighlightType highlightType,
                                                                     boolean isAfterEndOfLine);

  /**
   * @deprecated use {@link #createProblemDescriptor(PsiElement, PsiElement, String, ProblemHighlightType, boolean, LocalQuickFix...)} instead
   */
  @Deprecated
  @Contract(pure = true)
  public abstract @NotNull ProblemDescriptor createProblemDescriptor(@NotNull PsiElement startElement,
                                                                     @NotNull PsiElement endElement,
                                                                     @NotNull @InspectionMessage String descriptionTemplate,
                                                                     @NotNull ProblemHighlightType highlightType,
                                                                     @NotNull LocalQuickFix @Nullable ... fixes);


  /**
   * @deprecated use {@link #createNewGlobalContext()} instead
   */
  @Deprecated(forRemoval = true)
  @Contract(pure = true)
  public abstract @NotNull GlobalInspectionContext createNewGlobalContext(boolean reuse);

  //</editor-fold>

  @Contract(pure = true)
  public abstract @NotNull GlobalInspectionContext createNewGlobalContext();

  @ApiStatus.Internal
  public abstract @Unmodifiable @NotNull List<ProblemDescriptor> defaultProcessFile(@NotNull LocalInspectionTool tool, @NotNull PsiFile file);
}