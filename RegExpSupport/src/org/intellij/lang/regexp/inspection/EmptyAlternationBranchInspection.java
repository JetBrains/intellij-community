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

import com.intellij.codeInspection.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.intellij.lang.regexp.psi.RegExpBranch;
import org.intellij.lang.regexp.psi.RegExpElementVisitor;
import org.intellij.lang.regexp.psi.RegExpPattern;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class EmptyAlternationBranchInspection extends LocalInspectionTool {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "Empty branch in alternation";
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new EmptyAlternationBranchVisitor(holder);
  }

  private static class EmptyAlternationBranchVisitor extends RegExpElementVisitor {

    private final ProblemsHolder myHolder;

    public EmptyAlternationBranchVisitor(ProblemsHolder holder) {
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
        myHolder.registerProblem(branch.getPrevSibling(), "Empty branch in alternation", new DuplicateAlternationBranchFix());
      }
    }
  }

  private static class DuplicateAlternationBranchFix implements LocalQuickFix {

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Remove empty branch";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      element.getNextSibling().delete();
      element.delete();
    }
  }
}
