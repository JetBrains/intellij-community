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
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import org.intellij.lang.regexp.RegExpTT;
import org.intellij.lang.regexp.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class ReDoSInspection extends LocalInspectionTool {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "Exponential backtracking";
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new ReDoSVisitor(holder);
  }

  private static class ReDoSVisitor extends RegExpElementVisitor {

    private final ProblemsHolder myHolder;

    public ReDoSVisitor(ProblemsHolder holder) {
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
          myHolder.registerProblem(parent, "Potential exponential backtracking");
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
        if (element instanceof RegExpClosure) {
          final RegExpClosure closure = (RegExpClosure)element;
          if (closure.getQuantifier().isPossessive()) {
            return true;
          }
        }
        else if (element instanceof RegExpGroup) {
          final RegExpGroup group = (RegExpGroup)element;
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
