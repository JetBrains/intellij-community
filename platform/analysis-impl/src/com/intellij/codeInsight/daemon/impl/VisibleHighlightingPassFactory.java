// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ProperTextRange;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public abstract class VisibleHighlightingPassFactory  {
  private static final Key<ProperTextRange> HEADLESS_VISIBLE_AREA = Key.create("Editor.headlessVisibleArea");

  @NotNull
  public static ProperTextRange calculateVisibleRange(@NotNull Editor editor) {
    if (ApplicationManager.getApplication().isHeadlessEnvironment() && !ApplicationManager.getApplication().isUnitTestMode()) {
      ProperTextRange textRange = editor.getUserData(HEADLESS_VISIBLE_AREA);
      ProperTextRange entireTextRange = new ProperTextRange(0, editor.getDocument().getTextLength());
      if (textRange != null && entireTextRange.contains(textRange)) {
        return textRange;
      }
      return entireTextRange;
    }

    Rectangle rect = editor.getScrollingModel().getVisibleArea();
    LogicalPosition startPosition = editor.xyToLogicalPosition(new Point(rect.x, rect.y));

    int visibleStart = editor.logicalPositionToOffset(startPosition);
    LogicalPosition endPosition = editor.xyToLogicalPosition(new Point(rect.x + rect.width, rect.y + rect.height));

    int visibleEnd = editor.logicalPositionToOffset(new LogicalPosition(endPosition.line + 1, 0));

    return new ProperTextRange(visibleStart, Math.max(visibleEnd, visibleStart));
  }

  public static void setVisibleRangeForHeadlessMode(@NotNull Editor editor, @NotNull ProperTextRange range) {
    if (range.getEndOffset() > editor.getDocument().getTextLength()) {
      throw new IllegalArgumentException("Invalid range: " + range + "; document length: " + editor.getDocument().getTextLength());
    }
    editor.putUserData(HEADLESS_VISIBLE_AREA, range);
  }
}
