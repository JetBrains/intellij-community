package com.intellij.codeInsight.daemon.impl;

import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;

public interface IndentsPassFilter {
    LanguageExtension<IndentsPassFilter> EXTENSION_POINT = new LanguageExtension<>("com.intellij.daemon.indentsPassFilter");

    boolean shouldUseIndentPass(@NotNull final Editor editor);
}
