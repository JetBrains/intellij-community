package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.util.ProperTextRange;
import com.sun.istack.internal.NotNull;

import java.awt.*;

/**
 * Created by jetzajac on 02/02/16.
 */
public class VisibleRangeCalculatorImpl implements VisibleRangeCalculator {
  @Override
  @NotNull
  public ProperTextRange getVisibleTextRange(@NotNull Editor editor) {
    if (ApplicationManager.getApplication().isHeadlessEnvironment() && !ApplicationManager.getApplication().isUnitTestMode()) {
      return new ProperTextRange(0, editor.getDocument().getTextLength());
    }

    Rectangle rect = editor.getScrollingModel().getVisibleArea();
    LogicalPosition startPosition = editor.xyToLogicalPosition(new Point(rect.x, rect.y));

    int visibleStart = editor.logicalPositionToOffset(startPosition);
    LogicalPosition endPosition = editor.xyToLogicalPosition(new Point(rect.x + rect.width, rect.y + rect.height));

    int visibleEnd = editor.logicalPositionToOffset(new LogicalPosition(endPosition.line + 1, 0));

    return new ProperTextRange(visibleStart, Math.max(visibleEnd, visibleStart));
  }
}
