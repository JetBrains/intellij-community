// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.ide.plugins.PluginManagerConfigurable;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class InstallButton extends ColorButton {
  private static final Color GreenColor = new JBColor(0x5D9B47, 0x2B7B50);

  private static final Color FillForegroundColor = JBColor.namedColor("Plugins.Button.installFillForeground", WhiteForeground);
  private static final Color FillBackgroundColor = JBColor.namedColor("Plugins.Button.installFillBackground", GreenColor);

  private static final Color ForegroundColor = JBColor.namedColor("Plugins.Button.installForeground", GreenColor);
  private static final Color BackgroundColor =
    JBColor.namedColor("Plugins.Button.installBackground", PluginManagerConfigurable.MAIN_BG_COLOR);

  @SuppressWarnings("UseJBColor")
  private static final Color FocusedBackground = JBColor.namedColor("Plugins.Button.installFocusedBackground", new Color(0xE1F6DA));

  private static final Color BorderColor = JBColor.namedColor("Plugins.Button.installBorderColor", GreenColor);

  public InstallButton(boolean fill) {
    if (fill) {
      setTextColor(FillForegroundColor);
      setBgColor(FillBackgroundColor);
    }
    else {
      setTextColor(ForegroundColor);
      setFocusedTextColor(ForegroundColor);
      setBgColor(BackgroundColor);
    }

    setFocusedBgColor(FocusedBackground);
    setBorderColor(BorderColor);
    setFocusedBorderColor(BorderColor);

    setTextAndSize();
  }

  protected void setTextAndSize() {
    setText("Install");
    setWidth72(this);
  }

  @Override
  public void setEnabled(boolean b) {
    super.setEnabled(b);
  }

  public void setEnabled(boolean enabled, @Nullable String statusText) {
    super.setEnabled(enabled);
    if (enabled) {
      setTextAndSize();
    }
    else {
      setText(statusText);
      setWidth(this, 80);
    }
  }
}