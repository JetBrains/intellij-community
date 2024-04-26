// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf;

import com.intellij.ide.ui.UITheme;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated Use {@link UIThemeLookAndFeelInfoImpl}
 */
@Deprecated(forRemoval = true)
public class UIThemeBasedLookAndFeelInfo extends UIThemeLookAndFeelInfoImpl {
  public UIThemeBasedLookAndFeelInfo(@NotNull UITheme theme) {
    super(theme);
  }
}

