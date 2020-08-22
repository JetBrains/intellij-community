// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.pom.Navigatable;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

public final class EditSourceOnEnterKeyHandler {
  private static final KeyStroke ENTER = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0);
  private static final KeyboardShortcut ENTER_SHORTCUT = new KeyboardShortcut(ENTER, null);

  public static void install(@NotNull JTree tree) {
    install((JComponent)tree); // backward compatibility
  }

  public static void install(@NotNull JComponent component) {
    install(component, null);
  }

  public static void install(@NotNull JComponent component, @Nullable Runnable whenPerformed) {
    install(component, whenPerformed, Registry.is("edit.source.on.enter.key.request.focus.in.editor"));
  }

  private static void install(@NotNull JComponent component, @Nullable Runnable whenPerformed, boolean requestFocus) {
    onEnterKey(component, () -> {
      if (Registry.is("edit.source.on.enter.key.disabled")) return false;
      if (isOverriddenByAction(IdeActions.ACTION_EDIT_SOURCE)) return false;
      if (isOverriddenByAction(IdeActions.ACTION_VIEW_SOURCE)) return false;
      DataContext context = DataManager.getInstance().getDataContext(component);
      List<Navigatable> navigatables = getNavigatables(context);
      if (navigatables.isEmpty()) return false; // nowhere to navigate
      navigatables.forEach(navigatable -> navigatable.navigate(requestFocus));
      if (whenPerformed != null) whenPerformed.run();
      return true;
    });
  }

  private static boolean isOverriddenByAction(@NotNull String actionId) {
    KeymapManager manager = KeymapManager.getInstance();
    return manager != null && null != ContainerUtil.find(manager.getActiveKeymap().getShortcuts(actionId), ENTER_SHORTCUT::equals);
  }

  private static @NotNull List<Navigatable> getNavigatables(@NotNull DataContext context) {
    Navigatable[] array = CommonDataKeys.NAVIGATABLE_ARRAY.getData(context);
    if (array == null || array.length == 0) return Collections.emptyList();

    List<Navigatable> list = ContainerUtil.filter(array, Navigatable::canNavigateToSource);
    if (list.isEmpty() && Registry.is("edit.source.on.enter.key.non.source.navigation.enabled")) {
      for (Navigatable navigatable : array) {
        if (navigatable.canNavigate()) {
          // only one non-source navigatable should be supported
          // @see OpenSourceUtil#navigate(boolean, boolean, Iterable)
          return Collections.singletonList(navigatable);
        }
      }
    }
    return list;
  }

  private static void onEnterKey(@NotNull JComponent component, @NotNull BooleanSupplier action) {
    ActionListener listener = component.getActionForKeyStroke(ENTER);
    component.registerKeyboardAction(event -> {
      if (!action.getAsBoolean() && listener != null) {
        // perform previous action if the specified action is failed
        // it is needed to expand/collapse a tree node
        listener.actionPerformed(event);
      }
    }, ENTER, JComponent.WHEN_FOCUSED);
  }
}
