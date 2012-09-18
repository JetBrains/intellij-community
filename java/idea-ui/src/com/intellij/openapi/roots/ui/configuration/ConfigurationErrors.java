/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.util.PairProcessor;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
* User: spLeaner
*/
public interface ConfigurationErrors {
  Topic<ConfigurationErrors> TOPIC = Topic.create("Configuration Error", ConfigurationErrors.class, Topic.BroadcastDirection.NONE);

  void addError(@NotNull ConfigurationError error);
  void removeError(@NotNull ConfigurationError error);

  @SuppressWarnings({"UtilityClassWithoutPrivateConstructor"})
  class Bus {
    public static void addError(@NotNull final ConfigurationError error, @NotNull final Project project) {
      _do(error, project, new PairProcessor<ConfigurationErrors, ConfigurationError>() {
        @Override
        public boolean process(final ConfigurationErrors configurationErrors, final ConfigurationError configurationError) {
          configurationErrors.addError(configurationError);
          return false;
        }
      });
    }

    public static void removeError(@NotNull final ConfigurationError error, @NotNull final Project project) {
      _do(error, project, new PairProcessor<ConfigurationErrors, ConfigurationError>() {
        @Override
        public boolean process(final ConfigurationErrors configurationErrors, final ConfigurationError configurationError) {
          configurationErrors.removeError(configurationError);
          return false;
        }
      });
    }

    public static void _do(@NotNull final ConfigurationError error, @NotNull final Project project,
                           @NotNull final PairProcessor<ConfigurationErrors, ConfigurationError> fun) {
      if (!project.isInitialized()) {
        StartupManager.getInstance(project).runWhenProjectIsInitialized(new Runnable() {
           @Override
           public void run() {
             fun.process(project.getMessageBus().syncPublisher(TOPIC), error);
           }
         });

        return;
      }

      final MessageBus bus = project.getMessageBus();
      if (EventQueue.isDispatchThread()) fun.process(bus.syncPublisher(TOPIC), error);
      else {
        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
            fun.process(bus.syncPublisher(TOPIC), error);
          }
        });
      }
    }
  }
}
