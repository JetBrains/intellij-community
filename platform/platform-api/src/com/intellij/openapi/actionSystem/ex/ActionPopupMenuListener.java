// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.ex;

import com.intellij.openapi.actionSystem.ActionPopupMenu;
import org.jetbrains.annotations.NotNull;

/**
 * Allows to receive notifications when popup menus created from action groups are shown and closed.
 *
 */
public interface ActionPopupMenuListener {
  default void actionPopupMenuCreated(@NotNull ActionPopupMenu menu) {
  }

  default void actionPopupMenuReleased(@NotNull ActionPopupMenu menu) {
  }
}
