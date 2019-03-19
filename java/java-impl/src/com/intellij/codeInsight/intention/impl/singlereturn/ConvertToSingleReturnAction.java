// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl.singlereturn;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.ObjectUtils.tryCast;

public class ConvertToSingleReturnAction extends PsiElementBaseIntentionAction {

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    PsiCodeBlock block = findBlock(element);
    if (block == null) return;
    PsiType returnType = PsiTypesUtil.getMethodReturnType(block);
    if (returnType == null) return;
    PsiCodeBlock copy = (PsiCodeBlock)block.copy();

    process(project, copy, returnType, FinishMarker.defineFinishMarker(block, returnType));
    CodeStyleManager.getInstance(project).reformat(block.replace(copy));
  }

  private static void process(@NotNull Project project,
                              PsiCodeBlock block,
                              PsiType returnType,
                              FinishMarker marker) {
    ExitContext exitContext = new ExitContext(block, returnType, marker);

    while (true) {
      ProgressManager.checkCanceled();
      PsiReturnStatement returnStatement = getNonTerminalReturn(block);
      if (returnStatement == null) break;
      ReturnReplacementContext.replaceSingleReturn(project, block, exitContext, returnStatement);
    }
    exitContext.declareVariables();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    PsiCodeBlock block = findBlock(element);
    if (block == null) return false;
    PsiType returnType = PsiTypesUtil.getMethodReturnType(block);
    return returnType != null && getNonTerminalReturn(block) != null;
  }

  @NotNull
  @Override
  public String getText() {
    return getFamilyName();
  }

  @Nullable
  private static PsiCodeBlock findBlock(PsiElement element) {
    PsiParameterListOwner owner = PsiTreeUtil.getParentOfType(element, PsiParameterListOwner.class, false, PsiCodeBlock.class);
    if (owner == null) return null;
    return tryCast(owner.getBody(), PsiCodeBlock.class);
  }

  private static PsiReturnStatement getNonTerminalReturn(@NotNull PsiCodeBlock block) {
    PsiStatement lastStatement = ArrayUtil.getLastElement(block.getStatements());
    if (lastStatement == null) return null;
    class Visitor extends JavaRecursiveElementWalkingVisitor {
      private PsiReturnStatement myReturnStatement;

      @Override
      public void visitReturnStatement(PsiReturnStatement statement) {
        super.visitReturnStatement(statement);
        if (lastStatement != statement) {
          myReturnStatement = statement;
          stopWalking();
        }
      }

      @Override
      public void visitExpression(PsiExpression expression) {}

      @Override
      public void visitLambdaExpression(PsiLambdaExpression expression) {}

      @Override
      public void visitClass(PsiClass aClass) {}
    }
    Visitor visitor = new Visitor();
    block.accept(visitor);
    return visitor.myReturnStatement;
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.convert.to.single.return.name");
  }
}
