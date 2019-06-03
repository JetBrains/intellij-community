// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.ide.plugins.PluginManagerConfigurable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.options.newEditor.SettingsDialog;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

/**
 * @author Alexander Lobas
 */
public class RestartButton extends InstallButton {
  public RestartButton(@NotNull MyPluginModel pluginModel) {
    super(true);
    addActionListener(e -> {
      if (PluginManagerConfigurable.showRestartDialog() != Messages.YES) {
        return;
      }

      pluginModel.needRestart = true;
      pluginModel.createShutdownCallback = false;

      DialogWrapper settings = DialogWrapper.findInstance(this);
      assert settings instanceof SettingsDialog : settings;
      ((SettingsDialog)settings).applyAndClose(false /* will be saved on app exit */);

      Application application = ApplicationManager.getApplication();
      TransactionGuard.submitTransaction(application, () -> ((ApplicationImpl)application).exit(true, false, true));
    });
  }

  @Override
  protected void setTextAndSize() {
    setText("Restart IDE");
  }
}