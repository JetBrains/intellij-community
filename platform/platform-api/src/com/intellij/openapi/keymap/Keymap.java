// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.keymap;

import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.MouseShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.options.Scheme;
import com.intellij.openapi.util.NlsSafe;
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
  @NlsSafe String getPresentableName();

  @Nullable
  Keymap getParent();

  boolean canModify();

  /**
   * @return Action ids including parent keymap ids
   * Weakly consistent when called from background thread (may not reflect all the ongoing updates). 
   */
  @NotNull
  Collection<String> getActionIdList();

  /**
   * @return array of all action IDs registered in this Keymap or its parent keymaps.
   * Weakly consistent when called from background thread (may not reflect all the ongoing updates). 
   */
  String @NotNull [] getActionIds();

  /**
   * @return all keyboard shortcuts for the action with the specified {@code actionId}
   * or an empty array if the action doesn't have any keyboard shortcut.
   * 
   * Can be called in background thread.
   */
  // 60 external usages - actionId cannot be marked as NotNull
  Shortcut @NotNull [] getShortcuts(@Nullable String actionId);

  /**
   * @return all actions including parent keymap that have the specified first keystroke.
   * If there are no such actions, then the method returns an empty array.
   */
  @NotNull String @NotNull [] getActionIds(@NotNull KeyStroke firstKeyStroke);

  /**
   * @return all actions that have the specified first and second keystrokes. If there are no
   * such actions, then the method returns an empty array.
   */
  String[] getActionIds(@NotNull KeyStroke firstKeyStroke, @Nullable KeyStroke secondKeyStroke);

  /**
   * @deprecated Use {@link #getActionIdList(Shortcut)}
   */
  @Deprecated
  @NotNull String @NotNull [] getActionIds(@NotNull Shortcut shortcut);

  @NotNull List<String> getActionIdList(@NotNull Shortcut shortcut);

  /**
   * @return all actions with specified mouse shortcut.
   */
  @NotNull List<@NotNull String> getActionIds(@NotNull MouseShortcut shortcut);

  void addShortcut(@NotNull String actionId, @NotNull Shortcut shortcut);

  void removeShortcut(@NotNull String actionId, @NotNull Shortcut shortcut);

  @NotNull
  Map<String, List<KeyboardShortcut>> getConflicts(@NotNull String actionId, @NotNull KeyboardShortcut keyboardShortcut);

  void removeAllActionShortcuts(@NotNull String actionId);

  @NotNull
  Keymap deriveKeymap(@NotNull String newName);

  boolean hasActionId(@NotNull String actionId, @NotNull MouseShortcut shortcut);
}