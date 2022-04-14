// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

interface SuitableFontProvider {
  ExtensionPointName<SuitableFontProvider> EP_NAME = ExtensionPointName.create("com.intellij.ui.suitableFontProvider");

  Font getFontAbleToDisplay(int codePoint, int size, @JdkConstants.FontStyle int style, @NotNull String defaultFontFamily);
}
