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
import org.intellij.lang.regexp.psi.RegExpBranch;
import org.intellij.lang.regexp.psi.RegExpElementVisitor;
import org.intellij.lang.regexp.psi.RegExpGroup;
import org.intellij.lang.regexp.psi.RegExpPattern;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class UnnecessaryNonCapturingGroupInspection extends LocalInspectionTool {

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new UnnecessaryNonCapturingGroupVisitor(holder);
  }

  private static class UnnecessaryNonCapturingGroupVisitor extends RegExpElementVisitor {

    private final ProblemsHolder myHolder;

    private UnnecessaryNonCapturingGroupVisitor(ProblemsHolder holder) {
      myHolder = holder;
    }

    @Override
    public void visitRegExpGroup(RegExpGroup group) {
      super.visitRegExpGroup(group);
      if (group.getType() != RegExpGroup.Type.NON_CAPTURING) {
        return;
      }
      final PsiElement parent = group.getParent();
      if (parent instanceof RegExpBranch) {
        if (hasOneBranch(group.getPattern())) {
          registerProblem(group);
        }
        else {
          final PsiElement grandParent = parent.getParent();
          if (grandParent instanceof RegExpPattern && hasSingleAtom((RegExpPattern)grandParent)) {
            registerProblem(group);
          }
        }
      }
      else if (hasSingleAtom(group.getPattern())) {
        registerProblem(group);
      }
    }

    void registerProblem(RegExpGroup group) {
      myHolder.registerProblem(group.getFirstChild(),
                               RegExpBundle.message("inspection.warning.unnecessary.non.capturing.group", group.getText()),
                               new UnnecessaryNonCapturingGroupFix());
    }
  }

  private static boolean hasOneBranch(RegExpPattern pattern) {
    return pattern != null && pattern.getBranches().length == 1;
  }

  private static boolean hasSingleAtom(RegExpPattern pattern) {
    if (pattern == null) {
      return false;
    }
    final RegExpBranch[] branches = pattern.getBranches();
    return branches.length == 1 && branches[0].getAtoms().length == 1;
  }

  private static class UnnecessaryNonCapturingGroupFix implements LocalQuickFix {
    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
      return RegExpBundle.message("inspection.quick.fix.remove.unnecessary.non.capturing.group");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement().getParent();
      if (!(element instanceof RegExpGroup)) {
        return;
      }
      final RegExpGroup group = (RegExpGroup)element;
      RegExpReplacementUtil.replaceInContext(group, group.getPattern().getUnescapedText());
    }
  }
}
