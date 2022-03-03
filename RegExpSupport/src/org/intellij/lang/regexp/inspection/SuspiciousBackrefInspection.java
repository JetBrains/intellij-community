// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.regexp.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import org.intellij.lang.regexp.RegExpBundle;
import org.intellij.lang.regexp.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class SuspiciousBackrefInspection extends LocalInspectionTool {

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new SuspiciousBackrefVisitor(holder);
  }

  private static class SuspiciousBackrefVisitor extends RegExpElementVisitor {
    private final ProblemsHolder myHolder;

    SuspiciousBackrefVisitor(@NotNull ProblemsHolder holder) {
      myHolder = holder;
    }

    @Override
    public void visitRegExpBackref(RegExpBackref backref) {
      super.visitRegExpBackref(backref);
      final RegExpGroup target = backref.resolve();
      if (target == null) {
        return;
      }
      final RegExpBranch branch = PsiTreeUtil.getParentOfType(target, RegExpBranch.class);
      if (!PsiTreeUtil.isAncestor(branch, backref, true)) {
        final String message =
          RegExpBundle.message("inspection.warning.group.back.reference.are.in.different.branches", backref.getIndex());
        myHolder.registerProblem(backref, message);
      }
      else if (target.getTextOffset() > backref.getTextOffset()) {
        final String message = RegExpBundle.message("inspection.warning.group.defined.after.back.reference", backref.getIndex());
        myHolder.registerProblem(backref, message);
      }
    }

    @Override
    public void visitRegExpNamedGroupRef(RegExpNamedGroupRef groupRef) {
      super.visitRegExpNamedGroupRef(groupRef);
      final RegExpGroup target = groupRef.resolve();
      if (target == null) {
        return;
      }
      final RegExpBranch branch = PsiTreeUtil.getParentOfType(target, RegExpBranch.class);
      if (!PsiTreeUtil.isAncestor(branch, groupRef, true)) {
        final String message =
          RegExpBundle.message("inspection.warning.group.back.reference.are.in.different.branches", groupRef.getGroupName());
        myHolder.registerProblem(groupRef, message);
      }
      else if (target.getTextOffset() > groupRef.getTextOffset()) {
        final String message = RegExpBundle.message("inspection.warning.group.defined.after.back.reference", groupRef.getGroupName());
        myHolder.registerProblem(groupRef, message);
      }
    }
  }
}
