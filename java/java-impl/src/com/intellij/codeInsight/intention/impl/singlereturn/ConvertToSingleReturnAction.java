// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl.singlereturn;

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static com.intellij.util.ObjectUtils.tryCast;

public class ConvertToSingleReturnAction extends PsiElementBaseIntentionAction {

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    PsiCodeBlock block = findBlock(element);
    if (block == null) return;

    ThrowableComputable<PsiCodeBlock, RuntimeException> bodyGenerator = 
      () -> generateBody(project, block, ProgressManager.getInstance().getProgressIndicator());
    PsiCodeBlock replacement = ProgressManager.getInstance().runProcessWithProgressSynchronously(
      () -> ReadAction.compute(bodyGenerator), JavaBundle.message("intention.convert.to.single.return.progress.title"), true, project);
    if (replacement != null) {
      Runnable action = () -> CodeStyleManager.getInstance(project).reformat(block.replace(replacement));
      WriteCommandAction.runWriteCommandAction(project, JavaBundle.message("intention.convert.to.single.return.command.text"), null, action, element.getContainingFile());
    }
  }

  @Nullable
  private static PsiCodeBlock generateBody(@NotNull Project project, PsiCodeBlock block, ProgressIndicator indicator) {
    indicator.setIndeterminate(false);
    PsiType returnType = PsiTypesUtil.getMethodReturnType(block);
    if (returnType == null) return null;

    List<PsiReturnStatement> returns = Arrays.asList(PsiUtil.findReturnStatements(block));
    indicator.checkCanceled();
    indicator.setFraction(0.1);
    FinishMarker marker = FinishMarker.defineFinishMarker(block, returnType, returns);
    indicator.checkCanceled();
    indicator.setFraction(0.2);
    PsiCodeBlock copy = (PsiCodeBlock)block.copy();
    indicator.checkCanceled();
    indicator.setFraction(0.3);
    PsiLocalVariable variable = convertReturns(project, copy, returnType, marker, returns.size(), indicator);
    if (variable != null) {
      PsiJavaToken end = Objects.requireNonNull(copy.getRBrace());
      copy.addBefore(JavaPsiFacade.getElementFactory(project).createStatementFromText("return " + variable.getName() + ";", copy), end);
    }

    return copy;
  }

  public static PsiLocalVariable convertReturns(@NotNull Project project,
                                                PsiCodeBlock block,
                                                PsiType returnType,
                                                FinishMarker marker,
                                                int count,
                                                ProgressIndicator indicator) {
    ExitContext exitContext = new ExitContext(block, returnType, marker);
    int i=0;

    while (true) {
      indicator.checkCanceled();
      indicator.setFraction(0.3 + 0.6 * (i++) / count);
      ProgressManager.checkCanceled();
      PsiReturnStatement returnStatement = getNonTerminalReturn(block);
      if (returnStatement == null) break;
      ReturnReplacementContext.replaceSingleReturn(project, block, exitContext, returnStatement);
    }
    indicator.setFraction(0.9);
    PsiLocalVariable resultVariable = exitContext.declareVariables();
    indicator.setFraction(0.92);
    return resultVariable;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
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
    return JavaBundle.message("intention.convert.to.single.return.name");
  }
}
