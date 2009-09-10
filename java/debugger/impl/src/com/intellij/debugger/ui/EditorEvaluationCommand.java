package com.intellij.debugger.ui;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * @author lex
 */
public abstract class EditorEvaluationCommand<T> extends DebuggerContextCommandImpl {
  protected final PsiElement myElement;
  @Nullable private final Editor myEditor;
  protected final ProgressIndicator myProgressIndicator;
  private final DebuggerContextImpl myDebuggerContext;

  public EditorEvaluationCommand(@Nullable Editor editor, PsiElement expression, DebuggerContextImpl context,
                                 final ProgressIndicator indicator) {
    super(context);
    Project project = expression.getProject();
    myProgressIndicator = indicator;
    myEditor = editor;
    myElement = expression;
    myDebuggerContext = (DebuggerManagerEx.getInstanceEx(project)).getContext();
  }

  public Priority getPriority() {
    return Priority.HIGH;
  }

  protected abstract T evaluate(EvaluationContextImpl evaluationContext) throws EvaluateException;

  public T evaluate() throws EvaluateException {
    myProgressIndicator.setText(DebuggerBundle.message("progress.evaluating", myElement.getText()));

    try {
      T result = evaluate(myDebuggerContext.createEvaluationContext());

      if (myProgressIndicator.isCanceled()) throw new ProcessCanceledException();

      return result;
    } catch (final EvaluateException e) {
      if (myEditor != null) {
        DebuggerInvocationUtil.invokeLater(myDebuggerContext.getProject(), new Runnable() {
          public void run() {
            showEvaluationHint(myEditor, myElement, e);
          }
        }, myProgressIndicator.getModalityState());
      }
      throw e;
    }
  }

  public static void showEvaluationHint(final Editor myEditor, final PsiElement myElement, final EvaluateException e) {
    if (myEditor.isDisposed() || !myEditor.getComponent().isVisible()) return;

    HintManager.getInstance().showErrorHint(myEditor, e.getMessage(), myElement.getTextRange().getStartOffset(),
                                            myElement.getTextRange().getEndOffset(), HintManagerImpl.UNDER,
                                            HintManagerImpl.HIDE_BY_ESCAPE | HintManagerImpl.HIDE_BY_TEXT_CHANGE,
                                            1500);
  }

}
