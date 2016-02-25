/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.debugger.codeinsight;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.generation.surroundWith.JavaExpressionSurrounder;
import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.evaluation.DefaultCodeFragmentFactory;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressWindowWithNotification;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: lex
 * Date: Jul 17, 2003
 * Time: 7:51:01 PM
 */
public class JavaWithRuntimeCastSurrounder extends JavaExpressionSurrounder {

  public String getTemplateDescription() {
    return CodeInsightBundle.message("surround.with.runtime.type.template");
  }

  public boolean isApplicable(PsiExpression expr) {
    if (!expr.isPhysical()) return false;
    PsiFile file = expr.getContainingFile();
    if (!(file instanceof PsiCodeFragment)) return false;
    if (!DefaultCodeFragmentFactory.isDebuggerFile(file)) {
      return false;
    }

    return RuntimeTypeEvaluator.isSubtypeable(expr);
  }

  public TextRange surroundExpression(Project project, Editor editor, PsiExpression expr) throws IncorrectOperationException {
    DebuggerContextImpl debuggerContext = (DebuggerManagerEx.getInstanceEx(project)).getContext();
    DebuggerSession debuggerSession = debuggerContext.getDebuggerSession();
    if (debuggerSession != null) {
      final ProgressWindowWithNotification progressWindow = new ProgressWindowWithNotification(true, expr.getProject());
      SurroundWithCastWorker worker = new SurroundWithCastWorker(editor, expr, debuggerContext, progressWindow);
      progressWindow.setTitle(DebuggerBundle.message("title.evaluating"));
      debuggerContext.getDebugProcess().getManagerThread().startProgress(worker, progressWindow);
    }
    return null;
  }

  private static class SurroundWithCastWorker extends RuntimeTypeEvaluator {
    private final Editor myEditor;

    public SurroundWithCastWorker(Editor editor, PsiExpression expression, DebuggerContextImpl context, final ProgressIndicator indicator) {
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
      DebuggerInvocationUtil.invokeLater(project, new Runnable() {
        public void run() {
          new WriteCommandAction(project, CodeInsightBundle.message("command.name.surround.with.runtime.cast")) {
            protected void run(@NotNull Result result) throws Throwable {
              try {
                PsiElementFactory factory = JavaPsiFacade.getInstance(myElement.getProject()).getElementFactory();
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
            }
          }.execute();
        }
      }, myProgressIndicator.getModalityState());
    }

  }
}
