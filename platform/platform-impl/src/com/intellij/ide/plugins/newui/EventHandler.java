// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author Alexander Lobas
 */
public abstract class EventHandler {
  public void connect(@NotNull PluginsGroupComponent container) {
  }

  public void addCell(@NotNull ListPluginComponent component, int index) {
  }

  public void addCell(@NotNull ListPluginComponent component, @Nullable ListPluginComponent anchor) {
  }

  public void removeCell(@NotNull ListPluginComponent component) {
  }

  public int getCellIndex(@NotNull ListPluginComponent component) {
    return -1;
  }

  public void add(@NotNull Component component) {
  }

  public void addAll(@NotNull Component component) {
    add(component);
    for (Component child : UIUtil.uiChildren(component)) {
      addAll(child);
    }
  }

  public void updateHover(@NotNull ListPluginComponent component) {
  }

  public void initialSelection(boolean scrollAndFocus) {
  }

  public @NotNull List<ListPluginComponent> getSelection() {
    return List.of();
  }

  public void setSelection(@NotNull ListPluginComponent component) {
    setSelection(component, true);
  }

  public void setSelection(@NotNull ListPluginComponent component, boolean scrollAndFocus) {
  }

  public void setSelection(@NotNull List<ListPluginComponent> components) {
  }

  public void updateSelection() {
  }

  public void clear() {
  }

  public void setSelectionListener(@Nullable Consumer<? super PluginsGroupComponent> listener) {
  }

  public void handleUpDown(@NotNull KeyEvent event) {
  }

  public enum SelectionType {
    SELECTION, HOVER, NONE
  }

  public static final int DELETE_CODE = SystemInfo.isMac ? KeyEvent.VK_BACK_SPACE : KeyEvent.VK_DELETE;

  public static @Nullable ShortcutSet getShortcuts(@NotNull String id) {
    AnAction action = ActionManager.getInstance().getAction(id);
    return action == null ? null : action.getShortcutSet();
  }

  public static @Nullable Runnable addGlobalAction(@NotNull JComponent component, @NotNull Object actionInfo, @NotNull Runnable callback) {
    MyAnAction localAction = new MyAnAction() {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        if (component.isShowing()) {
          callback.run();
        }
      }
    };

    if (actionInfo instanceof String) {
      AnAction action = ActionManager.getInstance().getAction((String)actionInfo);
      if (action == null) {
        return null;
      }
      localAction.copyShortcutFrom(action);
    }
    else if (actionInfo instanceof ShortcutSet) {
      localAction.setShortcutSet((ShortcutSet)actionInfo);
    }
    else {
      return null;
    }

    localAction.registerCustomShortcutSet(component.getRootPane(), null);

    return () -> localAction.unregisterCustomShortcutSet(component.getRootPane());
  }

  private abstract static class MyAnAction extends AnAction {
  }
}