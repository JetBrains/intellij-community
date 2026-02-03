// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public abstract class AbstractPainter implements Painter {
  private boolean isRepaintNeeded;

  private final List<Listener> listeners = ContainerUtil.createLockFreeCopyOnWriteList();

  @Override
  public boolean needsRepaint() {
    return isRepaintNeeded;
  }

  public void setNeedsRepaint(boolean needsRepaint) {
    setNeedsRepaint(needsRepaint, null);
  }

  public void setNeedsRepaint(boolean value, @Nullable JComponent dirtyComponent) {
    isRepaintNeeded = value;
    if (isRepaintNeeded) {
      fireNeedsRepaint(dirtyComponent);
    }
  }

  @Override
  public void addListener(@NotNull Listener listener) {
    listeners.add(listener);
  }

  @Override
  public void removeListener(@NotNull Listener listener) {
    listeners.remove(listener);
  }

  public @Nullable <T> T setNeedsRepaint(T oldValue, T newValue) {
    if (!isRepaintNeeded) {
      if (oldValue == null) {
        setNeedsRepaint(newValue != null);
      }
      else {
        setNeedsRepaint(!oldValue.equals(newValue));
      }
    }

    return newValue;
  }

  protected void fireNeedsRepaint(JComponent dirtyComponent) {
    for (Listener each : listeners) {
      each.onNeedsRepaint(this, dirtyComponent);
    }
  }

  @Override
  public final void paint(@NotNull Component component, @NotNull Graphics2D g) {
    isRepaintNeeded = false;
    executePaint(component, g);
  }

  public abstract void executePaint(@NotNull Component component, @NotNull Graphics2D g);
}
