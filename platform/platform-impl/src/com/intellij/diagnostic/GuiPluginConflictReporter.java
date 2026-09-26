// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.diagnostic.errordialog.PluginConflictDialog;
import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.plugins.PluginConflictReporter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.util.messages.SimpleMessageBusConnection;
import com.intellij.util.ui.EDT;
import org.jetbrains.annotations.NotNull;

import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

final class GuiPluginConflictReporter implements PluginConflictReporter {
  private final Set<ReportInfo> myReports = new LinkedHashSet<>();

  @Override
  public void reportConflict(@NotNull Collection<PluginId> foundPlugins, final boolean hasConflictWithPlatform) {
    if (foundPlugins.size() < 2) {
      Logger.getInstance(GuiPluginConflictReporter.class).error("One should provide at least two conflicting classes to report",
                                                                new Throwable());
      return;
    }

    if (!LoadingState.APP_STARTED.isOccurred()) {
      boolean empty;
      synchronized (this) {
        empty = myReports.isEmpty();
        myReports.add(new ReportInfo(foundPlugins, hasConflictWithPlatform));
      }
      if (empty) {
        SimpleMessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().simpleConnect();
        connection.subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener() {
          @Override
          public void appStarted() {
            connection.disconnect();

            EventQueue.invokeLater(() -> {
              for (ReportInfo report : myReports) {
                new PluginConflictDialog(new ArrayList<>(report.foundPlugins), report.hasConflictWithPlatform).show();
              }
              myReports.clear();
            });
          }
        });
      }
      return;
    }

    Runnable task = () -> {
      new PluginConflictDialog(new ArrayList<>(foundPlugins), hasConflictWithPlatform).show();
    };

    if (EDT.isCurrentThreadEdt()) {
      task.run();
    }
    else {
      EventQueue.invokeLater(task);
    }
  }

  private static class ReportInfo {
    private final Collection<PluginId> foundPlugins;
    private final boolean hasConflictWithPlatform;

    private ReportInfo(@NotNull Collection<PluginId> foundPlugins, boolean hasConflictWithPlatform) {
      this.foundPlugins = foundPlugins;
      this.hasConflictWithPlatform = hasConflictWithPlatform;
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof ReportInfo info)) return false;
      return hasConflictWithPlatform == info.hasConflictWithPlatform && Objects.equals(foundPlugins, info.foundPlugins);
    }

    @Override
    public int hashCode() {
      return Objects.hash(foundPlugins, hasConflictWithPlatform);
    }
  }
}
