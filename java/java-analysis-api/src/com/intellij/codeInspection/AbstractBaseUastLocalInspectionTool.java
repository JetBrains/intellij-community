// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.uast.UastVisitorAdapter;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UField;
import org.jetbrains.uast.UFile;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

import java.util.Arrays;
import java.util.Objects;

public abstract class AbstractBaseUastLocalInspectionTool extends LocalInspectionTool {
  private static final Condition<PsiElement> PROBLEM_ELEMENT_CONDITION = Conditions.and(Conditions.instanceOf(PsiFile.class, PsiClass.class, PsiMethod.class, PsiField.class), Conditions.notInstanceOf(PsiTypeParameter.class));

  /**
   * Override this to report problems at method level.
   *
   * @param method     to check.
   * @param manager    InspectionManager to ask for ProblemDescriptors from.
   * @param isOnTheFly true if called during on the fly editor highlighting. Called from Inspect Code action otherwise.
   * @return {@code null} if no problems found or not applicable at method level.
   */
  @Nullable
  public ProblemDescriptor[] checkMethod(@NotNull UMethod method, @NotNull InspectionManager manager, boolean isOnTheFly) {
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
  @Nullable
  public ProblemDescriptor[] checkClass(@NotNull UClass aClass, @NotNull InspectionManager manager, boolean isOnTheFly) {
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
  @Nullable
  public ProblemDescriptor[] checkField(@NotNull UField field, @NotNull InspectionManager manager, boolean isOnTheFly) {
    return null;
  }

  @Override
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new UastVisitorAdapter(new AbstractUastVisitor() {
      @Override
      public boolean visitClass(UClass node) {
        addDescriptors(checkClass(node, holder.getManager(), isOnTheFly));
        return true;
      }

      @Override
      public boolean visitMethod(UMethod node) {
        addDescriptors(checkMethod(node, holder.getManager(), isOnTheFly));
        return true;
      }

      @Override
      public boolean visitField(UField node) {
        addDescriptors(checkField(node, holder.getManager(), isOnTheFly));
        return true;
      }

      @Override
      public boolean visitFile(UFile node) {
        addDescriptors(checkFile(node.getPsi(), holder.getManager(), isOnTheFly));
        return true;
      }

      private void addDescriptors(final ProblemDescriptor[] descriptors) {
        if (descriptors != null) {
          for (ProblemDescriptor descriptor : descriptors) {
            // Substitution is required when reporting on light(non-physical) elements.
            // of course it is better to fix in on reporter side,
            // but it still not possible sometimes. So we will try to workaround here.
            PsiElement startSubstitutor = substitute(descriptor.getStartElement(), holder.getFile());
            PsiElement endSubstitutor = substitute(descriptor.getEndElement(), holder.getFile());

            if (startSubstitutor == descriptor.getStartElement() && endSubstitutor == descriptor.getEndElement()) {
              holder.registerProblem(descriptor);
            }
            else {
              QuickFix[] fixes = descriptor.getFixes();
              holder.registerProblem(holder.getManager().createProblemDescriptor(
                startSubstitutor,
                endSubstitutor,
                descriptor.getDescriptionTemplate(),
                descriptor.getHighlightType(),
                isOnTheFly,
                fixes != null ? ContainerUtil.findAllAsArray(fixes, LocalQuickFix.class) : LocalQuickFix.EMPTY_ARRAY
              ));
            }
          }
        }
      }

      @NotNull
      private PsiElement substitute(@NotNull PsiElement element, @NotNull PsiFile desiredFile) {
        if (inFile(element, desiredFile)) return element;
        PsiElement navigationElement = element.getNavigationElement();
        if (navigationElement == null) return element;
        if (inFile(navigationElement, desiredFile)) return navigationElement;

        // last resort
        PsiElement elementAtSamePosition = desiredFile.findElementAt(navigationElement.getTextRange().getStartOffset());
        if (elementAtSamePosition != null && Objects.equals(elementAtSamePosition.getText(), navigationElement.getText())) {
          return elementAtSamePosition;
        }
        return element; // it can't be helped
      }

      private boolean inFile(@NotNull PsiElement element, @NotNull PsiFile desiredFile) {
        PsiFile file = element.getContainingFile();
        if (file == null) return false;
        return file.getViewProvider() == desiredFile.getViewProvider();
      }

    });
  }

  @Override
  public PsiNamedElement getProblemElement(@NotNull final PsiElement psiElement) {
    return (PsiNamedElement)PsiTreeUtil.findFirstParent(psiElement, PROBLEM_ELEMENT_CONDITION);
  }
}
