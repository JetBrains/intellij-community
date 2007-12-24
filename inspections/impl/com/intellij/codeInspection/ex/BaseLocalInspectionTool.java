package com.intellij.codeInspection.ex;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.BaseJavaLocalInspectionTool;
import org.jetbrains.annotations.NotNull;

/**
 * User: anna
 * Date: Dec 27, 2004
 */
public abstract class BaseLocalInspectionTool extends BaseJavaLocalInspectionTool {
  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.WARNING;
  }

  public boolean isEnabledByDefault() {
    return true;
  }
}
