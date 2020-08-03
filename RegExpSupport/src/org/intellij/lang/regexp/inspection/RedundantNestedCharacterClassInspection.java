// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.regexp.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.intellij.lang.regexp.RegExpBundle;
import org.intellij.lang.regexp.psi.RegExpClass;
import org.intellij.lang.regexp.psi.RegExpClassElement;
import org.intellij.lang.regexp.psi.RegExpElementVisitor;
import org.intellij.lang.regexp.psi.RegExpIntersection;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class RedundantNestedCharacterClassInspection extends LocalInspectionTool {

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new RedundantNestedCharacterClassVisitor(holder);
  }

  private static class RedundantNestedCharacterClassVisitor extends RegExpElementVisitor {

    private final ProblemsHolder myHolder;

    RedundantNestedCharacterClassVisitor(ProblemsHolder holder) {
      myHolder = holder;
    }

    @Override
    public void visitRegExpClass(RegExpClass regExpClass) {
      super.visitRegExpClass(regExpClass);
      final PsiElement parent = regExpClass.getParent();
      // In JDK 9 the behaviour of negated character classes was changed, so we can never warn about them
      // JDK 8: [^a&&b] is the intersection of [^a] with [b], which equals [b]
      // JDK 9: [^a&&b] is the intersection of [a] and [b] (which is nothing), inverted, which equals everything.
      // see https://bugs.openjdk.java.net/browse/JDK-8189343
      // and http://mail.openjdk.java.net/pipermail/core-libs-dev/2011-June/006957.html
      if (parent instanceof RegExpClass) {
        final RegExpClass parentClass = (RegExpClass)parent;
        if (!parentClass.isNegated() && !regExpClass.isNegated()) {
          myHolder.registerProblem(regExpClass.getFirstChild(), RegExpBundle.message("inspection.warning.redundant.nested.character.class"),
                                   new RedundantNestedCharacterClassFix());
        }
      }
      else if (parent instanceof RegExpIntersection) {
        final PsiElement grandParent = parent.getParent();
        if (grandParent instanceof RegExpClass) {
          final RegExpClass grandparentClass = (RegExpClass)grandParent;
          if (!grandparentClass.isNegated() && !regExpClass.isNegated()) {
            myHolder.registerProblem(regExpClass.getFirstChild(), RegExpBundle.message("inspection.warning.redundant.nested.character.class"),
                                     new RedundantNestedCharacterClassFix());
          }
        }
      }
    }

    private static class RedundantNestedCharacterClassFix implements LocalQuickFix {

      @Override
      public @IntentionFamilyName @NotNull String getFamilyName() {
        return RegExpBundle.message("inspection.quick.fix.replace.redundant.character.class.with.contents");
      }

      @Override
      public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        final PsiElement element = descriptor.getPsiElement().getParent();
        if (element instanceof RegExpClass) {
          final RegExpClass regExpClass = (RegExpClass)element;
          final RegExpClassElement[] elements = regExpClass.getElements();
          final PsiElement parent = regExpClass.getParent();
          for (RegExpClassElement classElement : elements) {
            parent.addBefore(classElement, regExpClass);
          }
          regExpClass.delete();
        }
      }
    }
  }
}
