// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collections;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class EventHandler {
  public static final EventHandler EMPTY = new EventHandler();

  public void connect(@NotNull PluginsGroupComponent container) {
  }

  public void addCell(@NotNull CellPluginComponent component, int index) {
  }

  public void addCell(@NotNull CellPluginComponent component, @Nullable CellPluginComponent anchor) {
  }

  public void removeCell(@NotNull CellPluginComponent component) {
  }

  public void add(@NotNull Component component) {
  }

  public void addAll(@NotNull Component component) {
  }

  public void updateHover(@NotNull CellPluginComponent component) {
  }

  public void initialSelection(boolean scrollAndFocus) {
  }

  @NotNull
  public List<CellPluginComponent> getSelection() {
    return Collections.emptyList();
  }

  public void setSelection(@NotNull CellPluginComponent component) {
    setSelection(component, true);
  }

  public void setSelection(@NotNull CellPluginComponent component, boolean scrollAndFocus) {
  }

  public void clear() {
  }

  public enum SelectionType {
    SELECTION, HOVER, NONE
  }
}