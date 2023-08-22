// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.dnd;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public abstract class DnDManager {
  public static DnDManager getInstance() {
    return ApplicationManager.getApplication().getService(DnDManager.class);
  }

  public abstract void registerSource(@NotNull DnDSource source, @NotNull JComponent component);

  public abstract void registerSource(@NotNull DnDSource source, @NotNull JComponent component, @NotNull Disposable parentDisposable);

  public abstract void registerSource(@NotNull AdvancedDnDSource source);

  public abstract void unregisterSource(@NotNull DnDSource source, @NotNull JComponent component);

  public abstract void unregisterSource(@NotNull AdvancedDnDSource source);

  public abstract void registerTarget(DnDTarget target, JComponent component);

  public abstract void registerTarget(@NotNull DnDTarget target, @NotNull JComponent component, @NotNull Disposable parentDisposable);

  public abstract void unregisterTarget(DnDTarget target, JComponent component);

  @Nullable
  public abstract Component getLastDropHandler();
}
