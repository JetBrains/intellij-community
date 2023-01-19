// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public abstract class AbstractPainter implements Painter {
  private boolean myNeedsRepaint;

  private final List<Listener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  @Override
  public boolean needsRepaint() {
    return myNeedsRepaint;
  }

  public void setNeedsRepaint(boolean needsRepaint) {
    setNeedsRepaint(needsRepaint, null);
  }

  public void setNeedsRepaint(boolean needsRepaint, @Nullable JComponent dirtyComponent) {
    myNeedsRepaint = needsRepaint;
    if (myNeedsRepaint) {
      fireNeedsRepaint(dirtyComponent);
    }
  }

  @Override
  public void addListener(@NotNull Listener listener) {
    myListeners.add(listener);
  }

  @Override
  public void removeListener(@NotNull Listener listener) {
    myListeners.remove(listener);
  }

  public @Nullable <T> T setNeedsRepaint(T oldValue, T newValue) {
    if (!myNeedsRepaint) {
      if (oldValue != null) {
        setNeedsRepaint(!oldValue.equals(newValue));
      }
      else if (newValue != null) {
        setNeedsRepaint(true);
      }
      else {
        setNeedsRepaint(false);
      }
    }

    return newValue;
  }

  protected void fireNeedsRepaint(JComponent dirtyComponent) {
    for (Listener each : myListeners) {
      each.onNeedsRepaint(this, dirtyComponent);
    }
  }

  @Override
  public final void paint(Component component, Graphics2D g) {
    myNeedsRepaint = false;
    executePaint(component, g);
  }

  public abstract void executePaint(final Component component, final Graphics2D g);
}
