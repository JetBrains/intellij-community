// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navigationToolbar.ui;

import com.intellij.ui.Gray;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author Konstantin Bulenkov
 * @deprecated unused in ide.navBar.v2. If you do a change here, please also update v2 implementation
 */
@Deprecated
final class DarculaNavBarUI extends CommonNavBarUI {
  @Nullable
  @Override
  public Color getForeground(boolean selected, boolean focused, boolean inactive) {
    if (inactive) return Gray._140;
    return super.getForeground(selected, focused, false);
  }
}
