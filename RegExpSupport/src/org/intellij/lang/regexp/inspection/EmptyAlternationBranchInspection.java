// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.regexp.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.intellij.lang.regexp.RegExpBundle;
import org.intellij.lang.regexp.psi.RegExpBranch;
import org.intellij.lang.regexp.psi.RegExpElementVisitor;
import org.intellij.lang.regexp.psi.RegExpPattern;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class EmptyAlternationBranchInspection extends LocalInspectionTool {

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new EmptyAlternationBranchVisitor(holder);
  }

  private static class EmptyAlternationBranchVisitor extends RegExpElementVisitor {

    private final ProblemsHolder myHolder;

    EmptyAlternationBranchVisitor(ProblemsHolder holder) {
      myHolder = holder;
    }

    @Override
    public void visitRegExpPattern(RegExpPattern pattern) {
      final RegExpBranch[] branches = pattern.getBranches();
      if (branches.length < 2) {
        return;
      }
      boolean emptyBranchSeen = false;
      for (int i = 0; i < branches.length; i++) {
        final RegExpBranch branch = branches[i];
        if (branch.getAtoms().length > 0) {
          continue;
        }
        if (i == 0) {
          // empty branch at beginning allowed
          emptyBranchSeen = true;
          continue;
        }
        if (!emptyBranchSeen && i == branches.length - 1) {
          // empty branch at end allowed, if no empty branch at beginning
          continue;
        }
        myHolder.registerProblem(branch.getPrevSibling(), RegExpBundle.message("inspection.warning.empty.branch.in.alternation"),
                                 new EmptyAlternationBranchFix());
      }
    }
  }

  private static class EmptyAlternationBranchFix extends PsiUpdateModCommandQuickFix {
    @Override
    public @Nls @NotNull String getFamilyName() {
      return RegExpBundle.message("inspection.quick.fix.remove.empty.branch");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (!(element.getParent() instanceof RegExpPattern)) return;
      element.getNextSibling().delete();
      element.delete();
    }
  }
}
