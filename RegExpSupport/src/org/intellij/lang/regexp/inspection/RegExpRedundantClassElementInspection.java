// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.regexp.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.regexp.RegExpBundle;
import org.intellij.lang.regexp.psi.*;
import org.jetbrains.annotations.NotNull;

import static org.intellij.lang.regexp.psi.RegExpSimpleClass.Kind.*;

public class RegExpRedundantClassElementInspection extends LocalInspectionTool {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new RegExpElementVisitor() {
      @Override
      public void visitRegExpClass(RegExpClass regExpClass) {
        RegExpClassElement[] classElements = regExpClass.getElements();
        boolean containsNonWordCharacterClass =
          ContainerUtil.exists(classElements, RegExpRedundantClassElementInspection::isAnyNonWordCharacter);
        boolean containsWordCharacterClass = ContainerUtil.exists(classElements, RegExpRedundantClassElementInspection::isAnyWordCharacter);
        for (RegExpClassElement element : classElements) {
          if (containsWordCharacterClass && isAnyDigit(element) || containsNonWordCharacterClass && isAnyNonDigit(element)) {
            String elementText = element.getText();
            holder.registerProblem(element, RegExpBundle.message("inspection.warning.redundant.class.element", elementText),
                                   new RemoveRedundantClassElement(elementText));
          }
        }
      }
    };
  }

  private static boolean isAnyDigit(RegExpClassElement classElement) {
    if (classElement instanceof RegExpSimpleClass) {
      return ((RegExpSimpleClass)classElement).getKind().equals(DIGIT);
    }
    if (classElement instanceof RegExpPosixBracketExpression) {
      String className = ((RegExpPosixBracketExpression)classElement).getClassName();
      return className != null && className.equals("digit");
    }
    return false;
  }

  private static boolean isAnyNonDigit(RegExpClassElement classElement) {
    return classElement instanceof RegExpSimpleClass && ((RegExpSimpleClass)classElement).getKind().equals(NON_DIGIT);
  }

  private static boolean isAnyWordCharacter(RegExpClassElement classElement) {
    if (classElement instanceof RegExpSimpleClass) {
      return ((RegExpSimpleClass)classElement).getKind().equals(WORD);
    }
    if (classElement instanceof RegExpPosixBracketExpression) {
      String className = ((RegExpPosixBracketExpression)classElement).getClassName();
      return className != null && className.equals("word");
    }
    return false;
  }

  private static boolean isAnyNonWordCharacter(RegExpClassElement classElement) {
    return classElement instanceof RegExpSimpleClass && ((RegExpSimpleClass)classElement).getKind().equals(NON_WORD);
  }

  private static class RemoveRedundantClassElement implements LocalQuickFix {
    private final String myClassElementText;

    private RemoveRedundantClassElement(String text) { myClassElementText = text; }

    @Override
    public @NotNull String getFamilyName() {
      return RegExpBundle.message("inspection.quick.fix.remove.redundant.class.element");
    }

    @Override
    public @NotNull String getName() {
      return RegExpBundle.message("inspection.quick.fix.remove.redundant.0.class.element", myClassElementText);
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      if (element == null) return;
      element.delete();
    }
  }
}
