// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.uast.UastHintedVisitorAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor;

public abstract class AbstractBaseUastLocalInspectionTool extends LocalInspectionTool {

  private static final Condition<PsiElement> PROBLEM_ELEMENT_CONDITION =
    Conditions.and(Conditions.instanceOf(PsiFile.class, PsiClass.class, PsiMethod.class, PsiField.class),
                   Conditions.notInstanceOf(PsiTypeParameter.class));

  private final Class<? extends UElement>[] myUElementsTypesHint;

  protected AbstractBaseUastLocalInspectionTool() {
    this(UFile.class, UClass.class, UField.class, UMethod.class);
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

  @Override
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
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
  public PsiNamedElement getProblemElement(@NotNull final PsiElement psiElement) {
    return (PsiNamedElement)PsiTreeUtil.findFirstParent(psiElement, PROBLEM_ELEMENT_CONDITION);
  }
}
