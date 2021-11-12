// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

interface PluginProblemReporter {
  static PluginProblemReporter getInstance() {
    if (ApplicationManager.getApplication() == null) {
      //if the application isn't initialized yet return silly implementation which reports all plugins problems as platform ones
      return new PluginProblemReporter() {
        @NotNull
        @Override
        public PluginException createPluginExceptionByClass(@NotNull String errorMessage,
                                                            @Nullable Throwable cause,
                                                            @NotNull Class pluginClass) {
          return new PluginException(errorMessage, cause, null);
        }
      };
    }

    return ApplicationManager.getApplication().getService(PluginProblemReporter.class);
  }

  @NotNull
  PluginException createPluginExceptionByClass(@NotNull String errorMessage, @Nullable Throwable cause, @NotNull Class<?> pluginClass);
}
