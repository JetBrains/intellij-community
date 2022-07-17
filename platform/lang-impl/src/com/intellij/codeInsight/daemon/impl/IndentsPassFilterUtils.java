package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.IndentGuideDescriptor;
import org.jetbrains.annotations.NotNull;

public class IndentsPassFilterUtils {
  public static boolean shouldRunIndentsPass(@NotNull Editor editor) {
    for (IndentsPassFilter indentsPassFilter : IndentsPassFilter.EXTENSION_POINT.getExtensionList()) {
      if (!indentsPassFilter.shouldRunIndentsPass(editor)) return false;
    }
    return true;
  }

  public static boolean shouldShowIndentGuide(@NotNull Editor editor, @NotNull IndentGuideDescriptor descriptor) {
    for (IndentsPassFilter indentsPassFilter : IndentsPassFilter.EXTENSION_POINT.getExtensionList()) {
      if (!indentsPassFilter.shouldShowIndentGuide(editor, descriptor)) return false;
    }
    return true;
  }

  private IndentsPassFilterUtils() {
  }
}
