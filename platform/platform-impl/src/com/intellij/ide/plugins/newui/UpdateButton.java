// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.ide.IdeBundle;
import com.intellij.ui.JBColor;

import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class UpdateButton extends ColorButton {
  private static final Color BlueColor = new JBColor(0x1D73BF, 0x134D80);
  private static final Color ForegroundColor = JBColor.namedColor("Plugins.Button.updateForeground", WhiteForeground);
  private static final Color BackgroundColor = JBColor.namedColor("Plugins.Button.updateBackground", BlueColor);
  private static final Color BorderColor = JBColor.namedColor("Plugins.Button.updateBorderColor", BlueColor);

  public UpdateButton() {
    setTextColor(ForegroundColor);
    setBgColor(BackgroundColor);
    setBorderColor(BorderColor);

    setText(IdeBundle.message("plugins.configurable.update.button"));
    setWidth72(this);
  }
}