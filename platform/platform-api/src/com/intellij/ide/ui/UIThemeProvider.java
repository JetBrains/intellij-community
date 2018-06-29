// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * @author Konstantin Bulenkov
 */
public final class UIThemeProvider {
  public static final ExtensionPointName<UIThemeProvider> EP_NAME = ExtensionPointName.create("com.intellij.themeProvider");

  @Attribute("path")
  public String path;

  @Attribute("id")
  public String id;

  @Nullable
  public UITheme createTheme() {
    try {
      return UITheme.loadFromJson(getClass().getResourceAsStream(path), id);
    }
    catch (IOException e) {
      Logger.getInstance(getClass()).warn(e);
      return null;
    }
  }
}
