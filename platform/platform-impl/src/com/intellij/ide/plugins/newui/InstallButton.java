// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.ide.plugins.PluginManagerConfigurableNew;

/**
 * @author Alexander Lobas
 */
public class InstallButton extends ColorButton {
  public InstallButton(boolean fill) {
    if (fill) {
      setTextColor(WhiteForeground);
      setBgColor(GreenColor);
    }
    else {
      setTextColor(GreenColor);
      setFocusedTextColor(GreenColor);
      setBgColor(PluginManagerConfigurableNew.MAIN_BG_COLOR);
    }

    setFocusedBgColor(GreenFocusedBackground);
    setBorderColor(GreenColor);
    setFocusedBorderColor(GreenColor);

    setTextAndSize();
  }

  protected void setTextAndSize() {
    setText("Install");
    setWidth72(this);
  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    if (!enabled) {
      setText("Installed");
      setWidth(this, 80);
    }
  }
}