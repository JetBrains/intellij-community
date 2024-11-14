// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.codeinsight;

import com.intellij.codeInsight.generation.surroundWith.JavaExpressionSurrounder;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.evaluation.DefaultCodeFragmentFactory;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class JavaWithRuntimeCastSurrounder extends JavaExpressionSurrounder {

  @Override
  public String getTemplateDescription() {
    return JavaDebuggerBundle.message("surround.with.runtime.type.template");
  }

  @Override
  public boolean isApplicable(PsiExpression expr) {
    if (!expr.isPhysical()) return false;
    PsiFile file = expr.getContainingFile();
    if (!(file instanceof PsiCodeFragment)) return false;
    if (!DefaultCodeFragmentFactory.isDebuggerFile(file)) {
      return false;
    }

    return RuntimeTypeEvaluator.isSubtypeable(expr);
  }

  @Override
  public TextRange surroundExpression(Project project, Editor editor, PsiExpression expr) throws IncorrectOperationException {
    DebuggerContextImpl debuggerContext = (DebuggerManagerEx.getInstanceEx(project)).getContext();
    DebuggerSession debuggerSession = debuggerContext.getDebuggerSession();
    if (debuggerSession != null) {
      final ProgressWindow progressWindow = new ProgressWindow(true, expr.getProject());
      SurroundWithCastWorker worker = new SurroundWithCastWorker(editor, expr, debuggerContext, progressWindow);
      progressWindow.setTitle(JavaDebuggerBundle.message("title.evaluating"));
      Objects.requireNonNull(debuggerContext.getManagerThread()).startProgress(worker, progressWindow);
    }
    return null;
  }

  private static class SurroundWithCastWorker extends RuntimeTypeEvaluator {
    private final Editor myEditor;

    SurroundWithCastWorker(Editor editor, PsiExpression expression, DebuggerContextImpl context, final ProgressIndicator indicator) {
      super(editor, expression, context, indicator);
      myEditor = editor;
    }

    @Override
    protected void typeCalculationFinished(@Nullable final PsiType type) {
      if (type == null) {
        return;
      }

      hold();
      final Project project = myElement.getProject();
      DebuggerInvocationUtil.invokeLater(project, () -> WriteCommandAction.writeCommandAction(project).withName(
        JavaDebuggerBundle.message("command.name.surround.with.runtime.cast")).run(() -> {
        try {
          PsiElementFactory factory = JavaPsiFacade.getElementFactory(myElement.getProject());
          PsiParenthesizedExpression parenth =
            (PsiParenthesizedExpression)factory.createExpressionFromText("((" + type.getCanonicalText() + ")expr)", null);
          //noinspection ConstantConditions
          ((PsiTypeCastExpression)parenth.getExpression()).getOperand().replace(myElement);
          parenth = (PsiParenthesizedExpression)JavaCodeStyleManager.getInstance(project).shortenClassReferences(parenth);
          PsiExpression expr = (PsiExpression)myElement.replace(parenth);
          TextRange range = expr.getTextRange();
          myEditor.getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());
          myEditor.getCaretModel().moveToOffset(range.getEndOffset());
          myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
        }
        catch (IncorrectOperationException e) {
          // OK here. Can be caused by invalid type like one for proxy starts with . '.Proxy34'
        }
        finally {
          release();
        }
      }), myProgressIndicator.getModalityState());
    }
  }
}
