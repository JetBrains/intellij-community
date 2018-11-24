/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
    return new DuplicateAlternationBranchVisitor(holder);
  }

  private static class DuplicateAlternationBranchVisitor extends RegExpElementVisitor {

    private final ProblemsHolder myHolder;

    public DuplicateAlternationBranchVisitor(ProblemsHolder holder) {
      myHolder = holder;
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
            if (reported.add(branch1)) {
              myHolder.registerProblem(branch1, "Duplicate branch in alternation", new DuplicateAlternationBranchFix());
            }
            if (reported.add(branch2)) {
              myHolder.registerProblem(branch2, "Duplicate branch in alternation", new DuplicateAlternationBranchFix());
            }
          }
        }
      }
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
