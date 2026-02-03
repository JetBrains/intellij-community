// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Alexander Lobas
 */
public final class PluginStateManager {
  private static final List<PluginStateListener> myStateListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  public static void addStateListener(@NotNull PluginStateListener listener) {
    myStateListeners.add(listener);
  }

  public static void removeStateListener(@NotNull PluginStateListener listener) {
    myStateListeners.remove(listener);
  }

  static void fireState(@NotNull IdeaPluginDescriptor descriptor, boolean install) {
    UIUtil.invokeLaterIfNeeded(() -> {
      for (PluginStateListener listener : myStateListeners) {
        if (install) {
          listener.install(descriptor);
        }
        else {
          listener.uninstall(descriptor);
        }
      }
    });
  }
}