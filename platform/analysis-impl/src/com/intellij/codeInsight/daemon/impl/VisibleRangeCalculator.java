package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.ProperTextRange;
import com.sun.istack.internal.NotNull;

/**
 * Created by jetzajac on 02/02/16.
 */
public interface VisibleRangeCalculator {
  class SERVICE {
    public static VisibleRangeCalculator getInstance() {
      return ServiceManager.getService(VisibleRangeCalculator.class);
    }
  }

  @NotNull
  ProperTextRange getVisibleTextRange(@NotNull Editor editor);
}
