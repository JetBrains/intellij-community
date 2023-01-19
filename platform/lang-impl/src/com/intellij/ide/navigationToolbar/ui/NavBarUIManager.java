// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navigationToolbar.ui;

import com.intellij.util.ui.StartupUiUtil;

/**
 * @author Konstantin Bulenkov
 * @deprecated unused in ide.navBar.v2. If you do a change here, please also update v2 implementation
 */
@Deprecated
public final class NavBarUIManager {
  public static final NavBarUI COMMON = new CommonNavBarUI();
  public static final NavBarUI DARCULA = new DarculaNavBarUI();

  public static NavBarUI getUI() {
    return StartupUiUtil.isUnderDarcula() ? DARCULA : COMMON;
  }
}
