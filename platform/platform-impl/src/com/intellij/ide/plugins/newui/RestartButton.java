// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.ide.IdeBundle;
import org.jetbrains.annotations.NotNull;

/**
 * @author Alexander Lobas
 */
final class RestartButton extends InstallButton {

  RestartButton(@NotNull MyPluginModel pluginModel) {
    super(true);
    addActionListener(e -> pluginModel.runRestartButton(this));
  }

  @Override
  protected void setTextAndSize() {
    setText(IdeBundle.message("plugins.configurable.restart.ide.button"));
  }
}