// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.util.PairProcessor;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public interface ConfigurationErrors {
  Topic<ConfigurationErrors> TOPIC = Topic.create("Configuration Error", ConfigurationErrors.class, Topic.BroadcastDirection.NONE);

  void addError(@NotNull ConfigurationError error);
  void removeError(@NotNull ConfigurationError error);

  @SuppressWarnings({"UtilityClassWithoutPrivateConstructor"})
  final
  class Bus {
    public static void addError(@NotNull final ConfigurationError error, @NotNull final Project project) {
      _do(error, project, (configurationErrors, configurationError) -> {
        configurationErrors.addError(configurationError);
        return false;
      });
    }

    public static void removeError(@NotNull final ConfigurationError error, @NotNull final Project project) {
      _do(error, project, (configurationErrors, configurationError) -> {
        configurationErrors.removeError(configurationError);
        return false;
      });
    }

    private static void _do(@NotNull final ConfigurationError error, @NotNull final Project project,
                           @NotNull final PairProcessor<? super ConfigurationErrors, ? super ConfigurationError> fun) {
      if (!project.isInitialized()) {
        StartupManager.getInstance(project).runWhenProjectIsInitialized(
          () -> fun.process(project.getMessageBus().syncPublisher(TOPIC), error));

        return;
      }

      final MessageBus bus = project.getMessageBus();
      if (EventQueue.isDispatchThread()) fun.process(bus.syncPublisher(TOPIC), error);
      else {
        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(() -> fun.process(bus.syncPublisher(TOPIC), error));
      }
    }
  }
}
