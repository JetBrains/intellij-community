/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.intellij.lang.regexp.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
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

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "Duplicate branch in alternation";
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new DuplicateAlternationBranchVisitor(holder, isOnTheFly);
  }

  private static class DuplicateAlternationBranchVisitor extends RegExpElementVisitor {

    private final ProblemsHolder myHolder;
    private final boolean myIsOnTheFly;

    public DuplicateAlternationBranchVisitor(ProblemsHolder holder, boolean isOnTheFly) {
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
      myHolder.registerProblem(branch1, "Duplicate branch in alternation", new DuplicateAlternationBranchFix());
    }
  }

  private static class DuplicateAlternationBranchFix implements LocalQuickFix {

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Remove duplicate branch";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
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
