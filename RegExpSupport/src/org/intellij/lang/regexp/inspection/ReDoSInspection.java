// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.regexp.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import org.intellij.lang.regexp.RegExpBundle;
import org.intellij.lang.regexp.RegExpTT;
import org.intellij.lang.regexp.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class ReDoSInspection extends LocalInspectionTool {

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new ReDoSVisitor(holder);
  }

  private static class ReDoSVisitor extends RegExpElementVisitor {

    private final ProblemsHolder myHolder;

    ReDoSVisitor(ProblemsHolder holder) {
      myHolder = holder;
    }

    @Override
    public void visitRegExpClosure(RegExpClosure closure) {
      if (!hasSuspiciousQuantifier(closure)) {
        return;
      }
      RegExpClosure parent = PsiTreeUtil.getParentOfType(closure, RegExpClosure.class);
      while (parent != null) {
        if (hasSuspiciousQuantifier(parent)) {
          if (isAtomic(closure)) {
            return;
          }
          myHolder.registerProblem(parent, RegExpBundle.message("inspection.warning.potential.exponential.backtracking"));
          return;
        }
        parent = PsiTreeUtil.getParentOfType(parent, RegExpClosure.class);
      }
    }

    private static boolean hasSuspiciousQuantifier(RegExpClosure closure) {
      final RegExpQuantifier quantifier = closure.getQuantifier();
      if (!quantifier.isCounted()) {
        final ASTNode token = quantifier.getToken();
        return !(token == null || token.getElementType() == RegExpTT.QUEST);
      }
      final RegExpNumber max = quantifier.getMax();
      if (max == null) {
        return true;
      }
      final Number value = max.getValue();
      return value == null || value.doubleValue() >= 10;
    }

    private static boolean isAtomic(RegExpAtom element) {
      while (element != null) {
        if (element instanceof RegExpClosure closure) {
          if (closure.getQuantifier().isPossessive()) {
            return true;
          }
        }
        else if (element instanceof RegExpGroup group) {
          if (group.getType() == RegExpGroup.Type.ATOMIC) {
            return true;
          }
        }
        element = PsiTreeUtil.getParentOfType(element, RegExpClosure.class, RegExpGroup.class);
      }
      return false;
    }
  }
}
