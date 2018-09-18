// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.ide.plugins.PluginManagerConfigurableNew;

/**
 * @author Alexander Lobas
 */
public class UpdateButton extends ColorButton {
  public UpdateButton() {
    setTextColor(WhiteForeground);
    setBgColor(BlueColor);
    setBorderColor(BlueColor);

    setText("Update");
    PluginManagerConfigurableNew.setWidth72(this);
  }
}