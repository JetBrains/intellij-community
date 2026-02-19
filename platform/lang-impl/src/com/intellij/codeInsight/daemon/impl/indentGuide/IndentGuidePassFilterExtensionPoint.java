// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.indentGuide;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.IndentGuideDescriptor;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;


final class IndentGuidePassFilterExtensionPoint {

  static ExtensionPointName<IndentGuidePassFilter> EP = new ExtensionPointName<>("com.intellij.daemon.indentGuidePassFilter");

  static boolean shouldRunIndentsPass(@NotNull Editor editor) {
    for (IndentGuidePassFilter indentsPassFilter : EP.getExtensionList()) {
      if (!indentsPassFilter.shouldRunIndentsPass(editor)) {
        return false;
      }
    }
    return true;
  }

  static boolean shouldShowIndentGuide(@NotNull Editor editor, @NotNull IndentGuideDescriptor descriptor) {
    for (IndentGuidePassFilter indentsPassFilter : EP.getExtensionList()) {
      if (!indentsPassFilter.shouldShowIndentGuide(editor, descriptor)) {
        return false;
      }
    }
    return true;
  }

  private IndentGuidePassFilterExtensionPoint() {
  }
}
