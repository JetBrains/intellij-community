// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.target;

import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

public final class RunTargetsEnabled {
  private static boolean forceEnable;

  public static boolean get() {
    if (forceEnable) {
      return true;
    }

    IdeaPluginDescriptorImpl corePluginDescriptor = (IdeaPluginDescriptorImpl)PluginManagerCore.getPlugin(PluginManagerCore.CORE_ID);
    return corePluginDescriptor != null &&
           corePluginDescriptor.getPluginAliases().contains(PluginId.getId("com.intellij.modules.run.targets")) &&
           !PluginManagerCore.isDisabled(PluginManagerCore.ULTIMATE_PLUGIN_ID)
      ;
  }

  @TestOnly
  public static void forceEnable(@NotNull Disposable parentDisposable) {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      throw new IllegalArgumentException("Can only be used in tests");
    }

    forceEnable = true;
    Disposer.register(parentDisposable, () -> forceEnable = false);
  }
}
