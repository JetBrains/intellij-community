// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.ComponentUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author Alexander Lobas
 */
public abstract class EventHandler {
  public void connect(@NotNull PluginsGroupComponent container) {
  }

  public void addCell(@NotNull CellPluginComponent component, int index) {
  }

  public void addCell(@NotNull CellPluginComponent component, @Nullable CellPluginComponent anchor) {
  }

  public void removeCell(@NotNull CellPluginComponent component) {
  }

  public int getCellIndex(@NotNull CellPluginComponent component) {
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

  public void setSelection(@NotNull List<? extends CellPluginComponent> components) {
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

  @Nullable
  public static ShortcutSet getShortcuts(@NotNull String id) {
    AnAction action = ActionManager.getInstance().getAction(id);
    return action == null ? null : action.getShortcutSet();
  }

  public static boolean check(@NotNull KeyboardShortcut shortcut, @Nullable ShortcutSet set) {
    if (set != null) {
      for (Shortcut test : set.getShortcuts()) {
        if (test.isKeyboard() && shortcut.startsWith(test)) {
          return true;
        }
      }
    }
    return false;
  }

  @NotNull
  protected static CellPluginComponent get(@NotNull ComponentEvent event) {
    //noinspection ConstantConditions
    return ComponentUtil.getParentOfType((Class<? extends CellPluginComponent>)CellPluginComponent.class, event.getComponent());
  }

  @Nullable
  public static Runnable addGlobalAction(@NotNull JComponent component, @NotNull Object actionInfo, @NotNull Runnable callback) {
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

  private static abstract class MyAnAction extends AnAction {
    @Override
    public void setShortcutSet(@NotNull ShortcutSet shortcutSet) {
      super.setShortcutSet(shortcutSet);
    }
  }
}