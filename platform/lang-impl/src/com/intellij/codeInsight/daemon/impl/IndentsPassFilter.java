package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.IndentGuideDescriptor;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

public interface IndentsPassFilter {
  ExtensionPointName<IndentsPassFilter> EXTENSION_POINT = new ExtensionPointName<>("com.intellij.daemon.indentsPassFilter");

  boolean shouldRunIndentsPass(@NotNull final Editor editor);

  boolean shouldShowIndentGuide(@NotNull final Editor editor, @NotNull final IndentGuideDescriptor descriptor);
}
