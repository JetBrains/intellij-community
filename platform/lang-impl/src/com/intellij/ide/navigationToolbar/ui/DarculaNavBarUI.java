// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.navigationToolbar.ui;

import com.intellij.ui.Gray;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
final class DarculaNavBarUI extends CommonNavBarUI {
  @Nullable
  @Override
  public Color getForeground(boolean selected, boolean focused, boolean inactive) {
    if (inactive) return Gray._140;
    return super.getForeground(selected, focused, false);
  }
}
