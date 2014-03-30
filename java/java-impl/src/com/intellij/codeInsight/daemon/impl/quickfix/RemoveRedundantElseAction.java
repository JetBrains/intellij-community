/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ven
 */
public class RemoveRedundantElseAction extends PsiElementBaseIntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.RemoveRedundantElseAction");

  @Override
  @NotNull
  public String getText() {
    return QuickFixBundle.message("remove.redundant.else.fix");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("remove.redundant.else.fix");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    if (element instanceof PsiKeyword &&
        element.getParent() instanceof PsiIfStatement &&
        PsiKeyword.ELSE.equals(element.getText())) {
      PsiIfStatement ifStatement = (PsiIfStatement)element.getParent();
      if (ifStatement.getElseBranch() == null) return false;
      PsiStatement thenBranch = ifStatement.getThenBranch();
      if (thenBranch == null) return false;
      PsiElement block = PsiTreeUtil.getParentOfType(ifStatement, PsiCodeBlock.class);
      if (block != null) {
        while (cantCompleteNormally(thenBranch, block)) {
          thenBranch = getPrevThenBranch(thenBranch);
          if (thenBranch == null) return true;
        }
        return false;
      }
    }
    return false;
  }

  @Nullable
  private static PsiStatement getPrevThenBranch(@NotNull PsiElement thenBranch) {
    final PsiElement ifStatement = thenBranch.getParent();
    final PsiElement parent = ifStatement.getParent();
    if (parent instanceof PsiIfStatement && ((PsiIfStatement)parent).getElseBranch() == ifStatement) {
      return ((PsiIfStatement)parent).getThenBranch();
    }
    return null;
  }

  private static boolean cantCompleteNormally(@NotNull PsiStatement thenBranch, PsiElement block) {
    try {
      ControlFlow controlFlow = ControlFlowFactory.getInstance(thenBranch.getProject()).getControlFlow(block, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance());
      int startOffset = controlFlow.getStartOffset(thenBranch);
      int endOffset = controlFlow.getEndOffset(thenBranch);
      return startOffset != -1 && endOffset != -1 && !ControlFlowUtil.canCompleteNormally(controlFlow, startOffset, endOffset);
    }
    catch (AnalysisCanceledException e) {
      return false;
    }
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) return;
    PsiIfStatement ifStatement = (PsiIfStatement)element.getParent();
    LOG.assertTrue(ifStatement != null && ifStatement.getElseBranch() != null);
    PsiStatement elseBranch = ifStatement.getElseBranch();
    if (elseBranch instanceof PsiBlockStatement) {
      PsiElement[] statements = ((PsiBlockStatement)elseBranch).getCodeBlock().getStatements();
      if (statements.length > 0) {
        ifStatement.getParent().addRangeAfter(statements[0], statements[statements.length-1], ifStatement);
      }
    } else {
      ifStatement.getParent().addAfter(elseBranch, ifStatement);
    }
    ifStatement.getElseBranch().delete();
  }
}
