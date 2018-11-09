// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.ProperTextRange;
import org.jetbrains.annotations.NotNull;

public abstract class VisibleHighlightingPassFactory  {
  @NotNull
  public static ProperTextRange calculateVisibleRange(@NotNull Editor editor) {
    return VisibleRangeCalculator.SERVICE.getInstance().getVisibleTextRange(editor);
  }
}
