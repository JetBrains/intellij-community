// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.diagnostic.errordialog.PluginConflictDialog;
import com.intellij.ide.plugins.PluginConflictReporter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;

final class GuiPluginConflictReporter implements PluginConflictReporter {
  @Override
  public void reportConflict(@NotNull Collection<PluginId> foundPlugins, final boolean hasConflictWithPlatform) {
    if (foundPlugins.size() < 2) {
      Logger.getInstance(GuiPluginConflictReporter.class).error("One should provide at least two conflicting classes to report",
                                                                new Throwable());
      return;
    }

    Runnable task = () -> {
      new PluginConflictDialog(new ArrayList<>(foundPlugins), hasConflictWithPlatform).show();
    };

    if (EventQueue.isDispatchThread()) {
      task.run();
    }
    else {
      EventQueue.invokeLater(task);
    }
  }
}
