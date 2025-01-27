// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

@ApiStatus.Internal
public interface SuitableFontProvider {
  ExtensionPointName<SuitableFontProvider> EP_NAME = new ExtensionPointName<>("com.intellij.ui.suitableFontProvider");

  Font getFontAbleToDisplay(int codePoint, int size, @JdkConstants.FontStyle int style, @NotNull String defaultFontFamily);
}
