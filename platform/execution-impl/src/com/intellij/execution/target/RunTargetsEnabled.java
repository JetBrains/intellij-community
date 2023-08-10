// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    return corePluginDescriptor != null && corePluginDescriptor.modules.contains(PluginId.getId("com.intellij.modules.run.targets"));
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
