// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

public abstract class EditorEvaluationCommand<T> extends DebuggerContextCommandImpl {
  protected final PsiElement myElement;
  private final @Nullable Editor myEditor;
  protected final ProgressIndicator myProgressIndicator;

  public EditorEvaluationCommand(@Nullable Editor editor, PsiElement expression, DebuggerContextImpl context,
                                 final ProgressIndicator indicator) {
    super(context);
    myProgressIndicator = indicator;
    myEditor = editor;
    myElement = expression;
  }

  @Override
  public Priority getPriority() {
    return Priority.HIGH;
  }

  protected abstract T evaluate(EvaluationContextImpl evaluationContext) throws EvaluateException;

  public T evaluate() throws EvaluateException {
    myProgressIndicator.setText(JavaDebuggerBundle.message("progress.evaluating", ReadAction.compute(myElement::getText)));

    try {
      T result = evaluate(getDebuggerContext().createEvaluationContext());

      ProgressIndicatorUtils.checkCancelledEvenWithPCEDisabled(myProgressIndicator);

      return result;
    }
    catch (final EvaluateException e) {
      if (myEditor != null) {
        DebuggerInvocationUtil.invokeLater(getDebuggerContext().getProject(),
                                           () -> showEvaluationHint(myEditor, myElement, e),
                                           myProgressIndicator.getModalityState());
      }
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
}
