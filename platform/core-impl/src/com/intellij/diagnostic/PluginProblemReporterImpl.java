// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.cl.PluginAwareClassLoader;
import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class PluginProblemReporterImpl implements PluginProblemReporter {
  @Override
  public @NotNull PluginException createPluginExceptionByClass(@NotNull String errorMessage,
                                                               @Nullable Throwable cause,
                                                               @NotNull Class<?> pluginClass) {
    ClassLoader classLoader = pluginClass.getClassLoader();
    PluginId pluginId;
    if (classLoader instanceof PluginAwareClassLoader) {
      pluginId = ((PluginAwareClassLoader)classLoader).getPluginId();
    }
    else {
      pluginId = PluginManager.getPluginByClassNameAsNoAccessToClass(pluginClass.getName());
    }
    return new PluginException(errorMessage, cause, pluginId);
  }
}
