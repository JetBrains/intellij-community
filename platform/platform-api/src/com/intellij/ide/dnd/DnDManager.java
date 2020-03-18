// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.dnd;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public abstract class DnDManager {
  public static DnDManager getInstance() {
    return ApplicationManager.getApplication().getService(DnDManager.class);
  }

  public abstract void registerSource(@NotNull DnDSource source, @NotNull JComponent component);

  public abstract void registerSource(@NotNull AdvancedDnDSource source);

  public abstract void unregisterSource(@NotNull DnDSource source, @NotNull JComponent component);

  public abstract void unregisterSource(@NotNull AdvancedDnDSource source);

  public abstract void registerTarget(DnDTarget target, JComponent component);

  public abstract void unregisterTarget(DnDTarget target, JComponent component);

  @Nullable
  public abstract Component getLastDropHandler();

  /**
   * This key is intended to switch on a smooth scrolling during drag-n-drop operations.
   * Note, that the target component must not use default auto-scrolling.
   *
   * @see java.awt.dnd.Autoscroll
   * @see JComponent#setAutoscrolls
   * @see JComponent#putClientProperty
   */
  @ApiStatus.Experimental
  public static final Key<Boolean> AUTO_SCROLL = Key.create("AUTO_SCROLL");
}
