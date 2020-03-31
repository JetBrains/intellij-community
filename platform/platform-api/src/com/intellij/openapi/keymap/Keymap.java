// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.keymap;

import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.MouseShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.options.Scheme;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface Keymap extends Scheme {
  //think about name
  @NotNull
  @Override
  String getName();

  //think about name
  String getPresentableName();

  @Nullable
  Keymap getParent();

  boolean canModify();

  /**
   * @return Action ids including parent keymap ids
   */
  @NotNull
  Collection<String> getActionIdList();

  String @NotNull [] getActionIds();

  /**
   * @return all keyboard shortcuts for the action with the specified {@code actionId}
   * or an empty array if the action doesn't have any keyboard shortcut.
   */
  // 60 external usages - actionId cannot be marked as NotNull
  Shortcut @NotNull [] getShortcuts(@Nullable String actionId);

  /**
   * @return all actions including parent keymap that have the specified first keystroke. If there are no
   * such actions then the method returns an empty array.
   */
  @NotNull String @NotNull [] getActionIds(@NotNull KeyStroke firstKeyStroke);

  /**
   * @return all actions that have the specified first and second keystrokes. If there are no
   * such actions then the method returns an empty array.
   */
  String[] getActionIds(@NotNull KeyStroke firstKeyStroke, @Nullable KeyStroke secondKeyStroke);

  @NotNull String[] getActionIds(@NotNull Shortcut shortcut);

  /**
   * @return all actions with specified mouse shortcut.
   */
  @NotNull List<@NotNull String> getActionIds(@NotNull MouseShortcut shortcut);

  void addShortcut(@NotNull String actionId, @NotNull Shortcut shortcut);

  void removeShortcut(@NotNull String actionId, @NotNull Shortcut shortcut);

  @NotNull
  Map<String, List<KeyboardShortcut>> getConflicts(@NotNull String actionId, @NotNull KeyboardShortcut keyboardShortcut);

  /**
   * @deprecated Use {@link KeymapManagerListener#TOPIC}
   */
  @Deprecated
  void addShortcutChangeListener(@NotNull Listener listener);

  /**
   * @deprecated Use {@link KeymapManagerListener#TOPIC}
   */
  @Deprecated
  void removeShortcutChangeListener(@NotNull Listener listener);

  void removeAllActionShortcuts(@NotNull String actionId);

  @NotNull
  Keymap deriveKeymap(@NotNull String newName);

  boolean hasActionId(@NotNull String actionId, @NotNull MouseShortcut shortcut);

  /**
   * @deprecated Use {@link KeymapManagerListener#TOPIC}
   */
  @Deprecated
  interface Listener {
    void onShortcutChanged(@NotNull String actionId);
  }
}