// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.ui.ComponentUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public abstract class VisibleHighlightingPassFactory {
  @NotNull
  public static ProperTextRange calculateVisibleRange(@NotNull Editor editor) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    Rectangle rect = editor.getScrollingModel().getVisibleArea();
    LogicalPosition startPosition = editor.xyToLogicalPosition(new Point(rect.x, rect.y));

    int visibleStart = editor.logicalPositionToOffset(startPosition);
    LogicalPosition endPosition = editor.xyToLogicalPosition(new Point(rect.x + rect.width, rect.y + rect.height));

    int visibleEnd = editor.logicalPositionToOffset(new LogicalPosition(endPosition.line + 1, 0));

    return new ProperTextRange(visibleStart, Math.max(visibleEnd, visibleStart));
  }

  @ApiStatus.Internal
  public static void setVisibleRangeForHeadlessMode(@NotNull Editor editor, @NotNull ProperTextRange range) {
    assert ApplicationManager.getApplication().isHeadlessEnvironment() : "Must be called in headless mode only";
    if (range.getEndOffset() > editor.getDocument().getTextLength()) {
      throw new IllegalArgumentException("Invalid range: " + range + "; document length: " + editor.getDocument().getTextLength());
    }

    Point viewPositionStart = editor.logicalPositionToXY(editor.offsetToLogicalPosition(range.getStartOffset()));
    Point viewPositionEnd = editor.logicalPositionToXY(editor.offsetToLogicalPosition(range.getEndOffset()));
    JScrollPane scrollPane = ComponentUtil.getScrollPane(editor.getContentComponent());
    if (scrollPane != null) {
      scrollPane.getViewport().setSize(editor.getContentComponent().getWidth(), Math.max(100, viewPositionEnd.y - viewPositionStart.y));
      editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
      scrollPane.getViewport().setViewPosition(viewPositionStart);
      scrollPane.getViewport().setExtentSize(new Dimension(editor.getContentComponent().getWidth(), Math.max(100, viewPositionEnd.y - viewPositionStart.y)));
      UIUtil.markAsFocused(editor.getContentComponent(), true); // to make ShowIntentionPass call its collectInformation()
    }
  }
}
