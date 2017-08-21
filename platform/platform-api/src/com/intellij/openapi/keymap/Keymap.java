/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  @NotNull
  String[] getActionIds();

  /**
   * @return all keyboard shortcuts for the action with the specified {@code actionId}
   * or an empty array if the action doesn't have any keyboard shortcut.
   */
  // 60 external usages - actionId cannot be marked as NotNull
  @NotNull
  Shortcut[] getShortcuts(@Nullable String actionId);

  /**
   * @return all actions including parent keymap that have the specified first keystroke. If there are no
   * such actions then the method returns an empty array.
   */
  @NotNull
  String[] getActionIds(@NotNull KeyStroke firstKeyStroke);

  /**
   * @return all actions that have the specified first and second keystrokes. If there are no
   * such actions then the method returns an empty array.
   */
  String[] getActionIds(@NotNull KeyStroke firstKeyStroke, @Nullable KeyStroke secondKeyStroke);

  String[] getActionIds(@NotNull Shortcut shortcut);

  /**
   * @return all actions with specified mouse shortcut.  If there are no
   * such action then the method returns an empty array.
   */
  @NotNull
  String[] getActionIds(@NotNull MouseShortcut shortcut);

  void addShortcut(@NotNull String actionId, @NotNull Shortcut shortcut);

  void removeShortcut(@NotNull String actionId, @NotNull Shortcut shortcut);

  @NotNull
  Map<String, List<KeyboardShortcut>> getConflicts(@NotNull String actionId, @NotNull KeyboardShortcut keyboardShortcut);

  void addShortcutChangeListener(@NotNull Listener listener);

  void removeShortcutChangeListener(@NotNull Listener listener);

  void removeAllActionShortcuts(@NotNull String actionId);

  @NotNull
  Keymap deriveKeymap(@NotNull String newName);

  boolean hasActionId(@NotNull String actionId, @NotNull MouseShortcut shortcut);

  interface Listener {
    void onShortcutChanged(String actionId);
  }
}