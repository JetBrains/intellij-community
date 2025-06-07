// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui;

import com.intellij.debugger.engine.JavaDebuggerEvaluator;
import com.intellij.openapi.editor.Document;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.xdebugger.XDebuggerTestUtil;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.evaluation.ExpressionInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

public abstract class JVMDebuggerEvaluatorTest extends LightPlatformCodeInsightTestCase {

  protected static final class ExpectedExpression {
    final @Nullable String expression;
    final boolean sideEffects;

    private ExpectedExpression(@Nullable String expression, boolean sideEffects) {
      this.expression = expression;
      this.sideEffects = sideEffects;
    }
  }

  protected static ExpectedExpression noExpressions() {
    return new ExpectedExpression(null, true /* doesn't matter */);
  }

  protected static ExpectedExpression expressionWithoutSideEffects(@NotNull String expr) {
    return new ExpectedExpression(expr, false);
  }

  protected static ExpectedExpression expressionWithSideEffects(@NotNull String expr) {
    return new ExpectedExpression(expr, true);
  }

  protected void checkExpressionRangeAtCaret(ExpectedExpression expected) {
    var editor = getEditor();
    var document = editor.getDocument();
    var caretOffset = editor.getCaretModel().getOffset();

    JavaDebuggerEvaluator evaluator = new JavaDebuggerEvaluator(null, null);

    if (expected.expression == null) {
      checkExpressionRangeImpl(null, false, evaluator, document, caretOffset);
      checkExpressionRangeImpl(null, true, evaluator, document, caretOffset);
    } else if (expected.sideEffects) {
      checkExpressionRangeImpl(null, false, evaluator, document, caretOffset);
      checkExpressionRangeImpl(expected.expression, true, evaluator, document, caretOffset);
    } else {
      checkExpressionRangeImpl(expected.expression, false, evaluator, document, caretOffset);
      checkExpressionRangeImpl(expected.expression, true, evaluator, document, caretOffset);
    }
  }

  private void checkExpressionRangeImpl(String expectedExpression,
                                        boolean sideEffectsAllowed,
                                        JavaDebuggerEvaluator evaluator,
                                        Document document,
                                        int caretOffset) {
    Promise<ExpressionInfo> infoAsync =
      evaluator.getExpressionInfoAtOffsetAsync(getProject(), document, caretOffset, sideEffectsAllowed);
    try {
      ExpressionInfo info = infoAsync.blockingGet(XDebuggerTestUtil.TIMEOUT_MS);
      if (info != null) {
        assertEquals("Text do not match (sideEffectsAllowed = " + sideEffectsAllowed + ")",
                     expectedExpression, document.getText(info.getTextRange()));
      }
      else {
        if (expectedExpression != null) {
          fail("Expected " + expectedExpression + ", but was null (sideEffectsAllowed = " + sideEffectsAllowed + ")");
        }
      }
    }
    catch (Exception e) {
      fail("Timeout while getting ExpressionInfo");
    }
  }

  protected EvaluationMode getEvalModeForSelection() {
    var selection = getEditor().getSelectionModel();
    var code = selection.getSelectedText();
    assertNotNull("Expected some selection", code);
    var start = selection.getSelectionStart();
    var end = selection.getSelectionEnd();

    JavaDebuggerEvaluator evaluator = new JavaDebuggerEvaluator(null, null);
    return evaluator.getEvaluationMode(code, start, end, getFile());
  }
}
