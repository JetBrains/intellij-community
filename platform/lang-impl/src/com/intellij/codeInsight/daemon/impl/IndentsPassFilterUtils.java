// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.IndentGuideDescriptor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class IndentsPassFilterUtils {
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
