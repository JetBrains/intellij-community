// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface PluginUtil {
  static PluginUtil getInstance() {
    return ApplicationManager.getApplication().getService(PluginUtil.class);
  }

  /**
   * @deprecated This function is dangerous because it can be used in class initializers.
   * This function was unintentionally exposed.
   * This function was not expected to be used outside the code which deals with plugins (i.e., platform code).
   */
  @ScheduledForRemoval
  @Deprecated
  @Nullable PluginId getCallerPlugin(int stackFrameCount);

  @Nullable PluginId findPluginId(@NotNull Throwable t);

  @Nullable @Nls String findPluginName(@NotNull PluginId pluginId);
}
