/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.FileTypeUtils;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class EmptyFinallyBlockInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("empty.finally.block.problem.descriptor");
  }

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    final Boolean canDeleteTry = (Boolean)infos[0];
    if (canDeleteTry) {
      return new RemoveTryFinallyBlockFix();
    }
    else {
      return new RemoveFinallyBlockFix();
    }
  }

  private static class RemoveTryFinallyBlockFix extends PsiUpdateModCommandQuickFix {
    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("remove.try.finally.block.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiTryStatement tryStatement = PsiTreeUtil.getParentOfType(element, PsiTryStatement.class);
      if (tryStatement == null || tryStatement.getResourceList() != null || tryStatement.getParent() == null) {
        return;
      }
      PsiCodeBlock tryBlock = tryStatement.getTryBlock();
      if (tryBlock == null) {
        return;
      }
      if (!(tryStatement.getParent() instanceof PsiCodeBlock)) {
        tryStatement = BlockUtils.expandSingleStatementToBlockStatement(tryStatement);
        tryBlock = Objects.requireNonNull(tryStatement.getTryBlock());
      }

      final PsiElement first = tryBlock.getFirstBodyElement();
      final PsiElement last = tryBlock.getLastBodyElement();
      if (first != null && last != null) {
        tryStatement.getParent().addRangeAfter(first, last, tryStatement);
      }

      tryStatement.delete();
    }
  }

  private static class RemoveFinallyBlockFix extends PsiUpdateModCommandQuickFix {

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("remove.finally.block.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiTryStatement tryStatement = PsiTreeUtil.getParentOfType(element, PsiTryStatement.class);
      if (tryStatement == null) {
        return;
      }
      final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
      if (finallyBlock == null) {
        return;
      }
      deleteUntilFinally(finallyBlock);
    }

    private static void deleteUntilFinally(PsiElement element) {
      if (element instanceof PsiJavaToken keyword) {
        final IElementType tokenType = keyword.getTokenType();
        if (tokenType.equals(JavaTokenType.FINALLY_KEYWORD)) {
          keyword.delete();
          return;
        }
      }
      deleteUntilFinally(element.getPrevSibling());
      if (!(element instanceof PsiWhiteSpace)) {
        element.delete();
      }
    }
  }

  @Override
  public boolean shouldInspect(@NotNull PsiFile file) {
    return !FileTypeUtils.isInServerPageFile(file);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new EmptyFinallyBlockVisitor();
  }

  private static class EmptyFinallyBlockVisitor extends BaseInspectionVisitor {
    @Override
    public void visitTryStatement(@NotNull PsiTryStatement statement) {
      super.visitTryStatement(statement);
      final PsiCodeBlock finallyBlock = statement.getFinallyBlock();
      if (finallyBlock == null) {
        return;
      }
      if (!finallyBlock.isEmpty()) {
        return;
      }
      final PsiElement[] children = statement.getChildren();
      for (final PsiElement child : children) {
        final String childText = child.getText();
        if (JavaKeywords.FINALLY.equals(childText)) {
          final boolean canDeleteTry = statement.getCatchBlocks().length == 0 && statement.getResourceList() == null;
          registerError(child, Boolean.valueOf(canDeleteTry));
          return;
        }
      }
    }
  }
}