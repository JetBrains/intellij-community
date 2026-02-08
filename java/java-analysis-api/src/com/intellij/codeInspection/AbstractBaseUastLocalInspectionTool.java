// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.uast.UastHintedVisitorAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UField;
import org.jetbrains.uast.UFile;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor;

public abstract class AbstractBaseUastLocalInspectionTool extends LocalInspectionTool {

  public static final Condition<PsiElement> PROBLEM_ELEMENT_CONDITION =
    Conditions.and(Conditions.instanceOf(PsiFile.class, PsiClass.class, PsiMethod.class, PsiField.class),
                   Conditions.notInstanceOf(PsiTypeParameter.class));

  private final Class<? extends UElement>[] myUElementsTypesHint;

  protected AbstractBaseUastLocalInspectionTool() {
    this(UFile.class, UClass.class, UField.class, UMethod.class, UParameter.class);
  }

  @SafeVarargs
  protected AbstractBaseUastLocalInspectionTool(Class<? extends UElement>... uElementsTypesHint) {
    myUElementsTypesHint = uElementsTypesHint;
  }

  /**
   * Override this to report problems at method level.
   *
   * @param method     to check.
   * @param manager    InspectionManager to ask for ProblemDescriptors from.
   * @param isOnTheFly true if called during on the fly editor highlighting. Called from Inspect Code action otherwise.
   * @return {@code null} if no problems found or not applicable at method level.
   */
  public ProblemDescriptor @Nullable [] checkMethod(@NotNull UMethod method, @NotNull InspectionManager manager, boolean isOnTheFly) {
    return null;
  }

  /**
   * Override this to report problems at class level.
   *
   * @param aClass     to check.
   * @param manager    InspectionManager to ask for ProblemDescriptors from.
   * @param isOnTheFly true if called during on the fly editor highlighting. Called from Inspect Code action otherwise.
   * @return {@code null} if no problems found or not applicable at class level.
   */
  public ProblemDescriptor @Nullable [] checkClass(@NotNull UClass aClass, @NotNull InspectionManager manager, boolean isOnTheFly) {
    return null;
  }

  /**
   * Override this to report problems at field level.
   *
   * @param field      to check.
   * @param manager    InspectionManager to ask for ProblemDescriptors from.
   * @param isOnTheFly true if called during on the fly editor highlighting. Called from Inspect Code action otherwise.
   * @return {@code null} if no problems found or not applicable at field level.
   */
  public ProblemDescriptor @Nullable [] checkField(@NotNull UField field, @NotNull InspectionManager manager, boolean isOnTheFly) {
    return null;
  }

  /**
   * Override this to report problems at parameter level.
   *
   * @param parameter      to check.
   * @param manager    InspectionManager to ask for ProblemDescriptors from.
   * @param isOnTheFly true if called during on the fly editor highlighting. Called from Inspect Code action otherwise.
   * @return {@code null} if no problems found or not applicable at parameter level.
   */
  public ProblemDescriptor @Nullable [] checkParameter(@NotNull UParameter parameter, @NotNull InspectionManager manager, boolean isOnTheFly) {
    return null;
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(final @NotNull ProblemsHolder holder, final boolean isOnTheFly) {
    return UastHintedVisitorAdapter.create(holder.getFile().getLanguage(), new AbstractUastNonRecursiveVisitor() {
      @Override
      public boolean visitClass(@NotNull UClass node) {
        addDescriptors(checkClass(node, holder.getManager(), isOnTheFly));
        return true;
      }

      @Override
      public boolean visitMethod(@NotNull UMethod node) {
        addDescriptors(checkMethod(node, holder.getManager(), isOnTheFly));
        return true;
      }

      @Override
      public boolean visitField(@NotNull UField node) {
        addDescriptors(checkField(node, holder.getManager(), isOnTheFly));
        return true;
      }

      @Override
      public boolean visitFile(@NotNull UFile node) {
        addDescriptors(checkFile(node.getPsi(), holder.getManager(), isOnTheFly));
        return true;
      }

      @Override
      public boolean visitParameter(@NotNull UParameter node) {
        addDescriptors(checkParameter(node, holder.getManager(), isOnTheFly));
        return true;
      }

      private void addDescriptors(final ProblemDescriptor[] descriptors) {
        if (descriptors != null) {
          for (ProblemDescriptor descriptor : descriptors) {
            holder.registerProblem(descriptor);
          }
        }
      }
    }, myUElementsTypesHint);
  }

  @Override
  public PsiNamedElement getProblemElement(final @NotNull PsiElement psiElement) {
    return (PsiNamedElement)PsiTreeUtil.findFirstParent(psiElement, PROBLEM_ELEMENT_CONDITION);
  }
}
