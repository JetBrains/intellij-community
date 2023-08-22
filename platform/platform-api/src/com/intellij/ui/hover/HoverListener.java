// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.hover;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.ui.ClientProperty;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;

@ApiStatus.Experimental
public abstract class HoverListener {
  private static final Key<List<HoverListener>> HOVER_LISTENER_LIST_KEY = Key.create("HoverListenerList");

  public abstract void mouseEntered(@NotNull Component component, int x, int y);

  public abstract void mouseMoved(@NotNull Component component, int x, int y);

  public abstract void mouseExited(@NotNull Component component);

  public final void addTo(@NotNull JComponent component, @NotNull Disposable parent) {
    addTo(component);
    Disposer.register(parent, () -> removeFrom(component));
  }

  public final void addTo(@NotNull JComponent component) {
    List<HoverListener> list = ClientProperty.get(component, HOVER_LISTENER_LIST_KEY);
    if (list == null) {
      component.putClientProperty(HOVER_LISTENER_LIST_KEY, list = ContainerUtil.createLockFreeCopyOnWriteList());
    }
    list.add(0, this);
  }

  public final void removeFrom(@NotNull JComponent component) {
    List<HoverListener> list = ClientProperty.get(component, HOVER_LISTENER_LIST_KEY);
    if (list != null) {
      list.remove(this);
    }
  }

  @ApiStatus.Internal
  public static @NotNull List<HoverListener> getAll(@NotNull Component component) {
    List<HoverListener> list = ClientProperty.get(component, HOVER_LISTENER_LIST_KEY);
    return list == null ? Collections.emptyList() : list;
  }
}
