// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf;

import com.intellij.ide.ui.UITheme;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

@ApiStatus.Internal
@ApiStatus.Experimental
public abstract class UIThemeLookAndFeelInfo extends UIManager.LookAndFeelInfo {
  private final @NotNull UITheme myTheme;

  public final @NotNull UITheme getTheme() {
    return myTheme;
  }

  protected UIThemeLookAndFeelInfo(@NotNull UITheme theme) {
    //todo one one should be used in the future
    super(theme.getName(), theme.isDark() ? "com.intellij.ide.ui.laf.darcula.DarculaLaf" : "com.intellij.ide.ui.laf.IntelliJLaf");
    myTheme = theme;
  }
}
