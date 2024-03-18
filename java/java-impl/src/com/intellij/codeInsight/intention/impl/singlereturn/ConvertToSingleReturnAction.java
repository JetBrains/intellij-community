// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl.singlereturn;

import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class ConvertToSingleReturnAction extends PsiUpdateModCommandAction<PsiParameterListOwner> {
  
  public ConvertToSingleReturnAction() {
    super(PsiParameterListOwner.class);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiParameterListOwner element, @NotNull ModPsiUpdater updater) {
    if (!(element.getBody() instanceof PsiCodeBlock block)) return;
    Project project = context.project();
    generateBody(project, block, new EmptyProgressIndicator());
  }

  private static void generateBody(@NotNull Project project, PsiCodeBlock block, ProgressIndicator indicator) {
    indicator.setIndeterminate(false);
    PsiType returnType = PsiTypesUtil.getMethodReturnType(block);
    if (returnType == null) return;

    List<PsiReturnStatement> returns = Arrays.asList(PsiUtil.findReturnStatements(block));
    indicator.checkCanceled();
    indicator.setFraction(0.1);
    FinishMarker marker = FinishMarker.defineFinishMarker(block, returnType, returns);
    indicator.checkCanceled();
    indicator.setFraction(0.2);
    indicator.checkCanceled();
    indicator.setFraction(0.3);
    PsiLocalVariable variable = convertReturns(project, block, returnType, marker, returns.size(), indicator);
    if (variable != null) {
      PsiJavaToken end = Objects.requireNonNull(block.getRBrace());
      block.addBefore(JavaPsiFacade.getElementFactory(project).createStatementFromText("return " + variable.getName() + ";", block), end);
    }
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
      if (i > count) {
        throw new IllegalStateException("Unable to convert to a single return form");
      }
    }
    indicator.setFraction(0.9);
    PsiLocalVariable resultVariable = exitContext.declareVariables();
    indicator.setFraction(0.92);
    return resultVariable;
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiParameterListOwner owner) {
    if (PsiTreeUtil.getParentOfType(context.findLeaf(), PsiParameterListOwner.class, true, PsiCodeBlock.class) != owner) return null;
    if (!(owner.getBody() instanceof PsiCodeBlock block)) return null;
    PsiType returnType = PsiTypesUtil.getMethodReturnType(block);
    if (returnType == null || getNonTerminalReturn(block) == null) return null;
    return Presentation.of(getFamilyName());
  }

  private static PsiReturnStatement getNonTerminalReturn(@NotNull PsiCodeBlock block) {
    PsiStatement lastStatement = ArrayUtil.getLastElement(block.getStatements());
    if (lastStatement == null) return null;
    class Visitor extends JavaRecursiveElementWalkingVisitor {
      private PsiReturnStatement myReturnStatement;

      @Override
      public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
        super.visitReturnStatement(statement);
        if (lastStatement != statement) {
          myReturnStatement = statement;
          stopWalking();
        }
      }

      @Override
      public void visitErrorElement(@NotNull PsiErrorElement element) {
        stopWalking();
      }

      @Override
      public void visitExpression(@NotNull PsiExpression expression) {}

      @Override
      public void visitLambdaExpression(@NotNull PsiLambdaExpression expression) {}

      @Override
      public void visitClass(@NotNull PsiClass aClass) {}
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
