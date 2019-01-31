// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.ide.plugins.PluginManagerCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class PluginProblemReporterImpl implements PluginProblemReporter {
  @NotNull
  @Override
  public PluginException createPluginExceptionByClass(@NotNull String errorMessage, @Nullable Throwable cause, @NotNull Class pluginClass) {
    return PluginManagerCore.createPluginException(errorMessage, cause, pluginClass);
  }
}
