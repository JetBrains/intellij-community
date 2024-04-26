/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.AbstractBaseUastLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.uast.UastHintedVisitorAdapter;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.util.UastControlFlowUtils;
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor;

public final class ReturnFromFinallyBlockInspection extends AbstractBaseUastLocalInspectionTool {

  @Override
  @NotNull
  public String getID() {
    return "ReturnInsideFinallyBlock";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return UastHintedVisitorAdapter.create(holder.getFile().getLanguage(),
                                           new ReturnFromFinallyBlockVisitor(holder),
                                           new Class[] {UReturnExpression.class});
  }

  private static final class ReturnFromFinallyBlockVisitor extends AbstractUastNonRecursiveVisitor {
    private final ProblemsHolder myHolder;

    private ReturnFromFinallyBlockVisitor(@NotNull ProblemsHolder holder) {
      myHolder = holder;
    }

    @Override
    public boolean visitReturnExpression(@NotNull UReturnExpression node) {
      PsiElement returnExpr = node.getSourcePsi();
      if (returnExpr != null && UastControlFlowUtils.isInFinallyBlock(node)) {
        myHolder.registerProblem(returnExpr, InspectionGadgetsBundle.message("return.from.finally.block.problem.descriptor"));
      }
      return false;
    }
  }
}