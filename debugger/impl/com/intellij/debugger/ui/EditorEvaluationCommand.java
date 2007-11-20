package com.intellij.debugger.ui;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.util.ProgressWindowWithNotification;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Mar 15, 2004
 * Time: 4:07:59 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class EditorEvaluationCommand<T> extends DebuggerContextCommandImpl {
  protected final PsiElement myElement;
  private final Editor myEditor;
  private final ProgressWindowWithNotification myProgressWindow;
  private final DebuggerContextImpl myDebuggerContext;

  public EditorEvaluationCommand(Editor editor, PsiElement expression, DebuggerContextImpl context) {
    super(context);
    Project project = expression.getProject();
    myProgressWindow = new ProgressWindowWithNotification(true, project);
    myEditor = editor;
    myElement = expression;
    myDebuggerContext = (DebuggerManagerEx.getInstanceEx(project)).getContext();
  }

  protected abstract T evaluate(EvaluationContextImpl evaluationContext) throws EvaluateException;

  public T evaluate() throws EvaluateException {
    getProgressWindow().setText(DebuggerBundle.message("progress.evaluating", myElement.getText()));

    try {
      T result = evaluate(myDebuggerContext.createEvaluationContext());

      if(getProgressWindow().isCanceled()) throw new ProcessCanceledException();

      return result;
    } catch (final EvaluateException e) {
      DebuggerInvocationUtil.invokeLater(myDebuggerContext.getProject(), new Runnable() {
        public void run() {
          showEvaluationHint(myEditor, myElement, e);
        }
      }, myProgressWindow.getModalityState());
      throw e;
    }
  }

  public static void showEvaluationHint(final Editor myEditor, final PsiElement myElement, final EvaluateException e) {
    if (myEditor.isDisposed() || !myEditor.getComponent().isVisible()) return;

    HintManager.getInstance().showErrorHint(myEditor, e.getMessage(), myElement.getTextRange().getStartOffset(),
                                            myElement.getTextRange().getEndOffset(), HintManager.UNDER,
                                            HintManager.HIDE_BY_ESCAPE | HintManager.HIDE_BY_TEXT_CHANGE,
                                            1500);
  }

  public ProgressWindowWithNotification getProgressWindow() {
    return myProgressWindow;
  }

  public Editor getEditor() {
    return myEditor;
  }
}
