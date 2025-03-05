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

import java.util.HashSet;
import java.util.Set;

/**
 * @author Bas Leijdekkers
 */
public class DuplicateAlternationBranchInspection extends LocalInspectionTool {

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new DuplicateAlternationBranchVisitor(holder, isOnTheFly);
  }

  private static class DuplicateAlternationBranchVisitor extends RegExpElementVisitor {

    private final ProblemsHolder myHolder;
    private final boolean myIsOnTheFly;

    DuplicateAlternationBranchVisitor(ProblemsHolder holder, boolean isOnTheFly) {
      myHolder = holder;
      myIsOnTheFly = isOnTheFly;
    }

    @Override
    public void visitRegExpPattern(RegExpPattern pattern) {
      final RegExpBranch[] branches = pattern.getBranches();
      if (branches.length < 2) {
        return;
      }
      final Set<RegExpBranch> reported = new HashSet<>(2);
      for (int i = 0; i < branches.length - 1; i++) {
        final RegExpBranch branch1 = branches[i];
        if (branch1.getAtoms().length == 0) {
          continue;
        }
        for (int j = i + 1; j < branches.length; j++) {
          final RegExpBranch branch2 = branches[j];
          if (RegExpEquivalenceChecker.areElementsEquivalent(branch1, branch2)) {
            if (reported.add(branch1) && myIsOnTheFly) {
              registerProblem(branch1);
            }
            if (reported.add(branch2)) {
              registerProblem(branch2);
            }
          }
        }
      }
    }

    private void registerProblem(RegExpBranch branch1) {
      myHolder.registerProblem(branch1, RegExpBundle.message("inspection.warning.duplicate.branch.in.alternation"), new DuplicateAlternationBranchFix());
    }
  }

  private static class DuplicateAlternationBranchFix extends PsiUpdateModCommandQuickFix {
    @Override
    public @Nls @NotNull String getFamilyName() {
      return RegExpBundle.message("inspection.quick.fix.remove.duplicate.branch");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (!(element instanceof RegExpBranch)) {
        return;
      }
      final PsiElement prevSibling = element.getPrevSibling();
      if (prevSibling != null) {
        prevSibling.delete();
      }
      else {
        final PsiElement nextSibling = element.getNextSibling();
        if (nextSibling != null) {
          nextSibling.delete();
        }
      }
      element.delete();
    }
  }
}
