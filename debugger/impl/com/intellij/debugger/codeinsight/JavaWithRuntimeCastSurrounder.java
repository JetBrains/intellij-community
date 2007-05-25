package com.intellij.debugger.codeinsight;

import com.intellij.codeInsight.generation.surroundWith.JavaExpressionSurrounder;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.EvaluatingComputable;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilderImpl;
import com.intellij.debugger.engine.evaluation.expression.ExpressionEvaluator;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.ui.DebuggerExpressionComboBox;
import com.intellij.debugger.ui.EditorEvaluationCommand;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.sun.jdi.Value;

/**
 * User: lex
 * Date: Jul 17, 2003
 * Time: 7:51:01 PM
 */
public class JavaWithRuntimeCastSurrounder extends JavaExpressionSurrounder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.codeinsight.SurroundWithRuntimeCastHandler");

  public String getTemplateDescription() {
    return CodeInsightBundle.message("surround.with.runtime.type.template");
  }

  public boolean isApplicable(PsiExpression expr) {
    PsiFile file = expr.getContainingFile();
    if (!(file instanceof PsiCodeFragment)) return false;
    return file.getUserData(DebuggerExpressionComboBox.KEY) != null;
  }

  public TextRange surroundExpression(Project project, Editor editor, PsiExpression expr) throws IncorrectOperationException {
    DebuggerContextImpl debuggerContext = (DebuggerManagerEx.getInstanceEx(project)).getContext();
    DebuggerSession debuggerSession = debuggerContext.getDebuggerSession();
    if (debuggerSession != null) {
      SurroundWithCastWorker worker = new SurroundWithCastWorker(editor, expr, debuggerContext);
      worker.getProgressWindow().setTitle(DebuggerBundle.message("title.evaluating"));
      debuggerContext.getDebugProcess().getManagerThread().startProgress(worker, worker.getProgressWindow());
    }
    return null;
  }

  private class SurroundWithCastWorker extends EditorEvaluationCommand<String> {
    public SurroundWithCastWorker(Editor editor, PsiExpression expression, DebuggerContextImpl context) {
      super(editor, expression, context);
    }

    protected void executeWriteCommand(final Project project, final Runnable runnable) {
      DebuggerInvocationUtil.invokeLater(project, new Runnable() {
          public void run() {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              public void run() {
                CommandProcessor.getInstance().executeCommand(project, runnable, CodeInsightBundle.message("command.name.surround.with.runtime.cast"), null);
              }
            });
          }
        }, getProgressWindow().getModalityState());
    }

    public void threadAction() {
      final String type;
      try {
        type = evaluate();
      }
      catch (ProcessCanceledException e) {
        return;
      }
      catch (EvaluateException e) {
        return;
      }

      final Project project = myElement.getProject();

      hold();

      executeWriteCommand(project, new Runnable() {
        public void run() {
          try {
            LOG.assertTrue(type != null);

            PsiElementFactory factory = myElement.getManager().getElementFactory();
            PsiParenthesizedExpression parenth = (PsiParenthesizedExpression) factory.createExpressionFromText("((" + type + ")expr)", null);
            PsiTypeCastExpression cast = (PsiTypeCastExpression) parenth.getExpression();
            cast.getOperand().replace(myElement);
            parenth = (PsiParenthesizedExpression)CodeStyleManager.getInstance(project).shortenClassReferences(parenth);
            PsiExpression expr  = (PsiExpression) myElement.replace(parenth);
            TextRange range = expr.getTextRange();
            getEditor().getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());
            getEditor().getCaretModel().moveToOffset(range.getEndOffset());
            getEditor().getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
          }
          catch (IncorrectOperationException e) {
            // OK here. Can be caused by invalid type like one for proxy starts with . '.Proxy34'
          }
          finally{
            release();
          }
        }
      });
    }

    protected String evaluate(EvaluationContextImpl evaluationContext) throws EvaluateException {
      final Project project = evaluationContext.getProject();

      ExpressionEvaluator evaluator = DebuggerInvocationUtil.commitAndRunReadAction(project, new EvaluatingComputable<ExpressionEvaluator>() {
        public ExpressionEvaluator compute() throws EvaluateException {
          return EvaluatorBuilderImpl.getInstance().build(myElement);
        }
      });

      final Value value = evaluator.evaluate(evaluationContext);
      if(value != null){
        return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
          public String compute() {
            return DebuggerUtilsEx.getQualifiedClassName(value.type().name(), project);
          }
        });
      }
      else {
        throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.surrounded.expression.null"));
      }
    }
  }
}
