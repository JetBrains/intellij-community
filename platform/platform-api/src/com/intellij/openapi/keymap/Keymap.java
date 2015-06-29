/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Map;

public interface Keymap extends Scheme {
  //think about name
  @NotNull
  @Override
  String getName();

  //think about name
  String getPresentableName();

  Keymap getParent();

  boolean canModify();

  /**
   * @return Action ids including ids of parent keymap
   */
  String[] getActionIds();

  /**
   * @return all keyboard shortcuts for the action with the specified <code>actionId</code>
   * or an empty array if the action doesn't have any keyboard shortcut.
   */
  @NotNull
  Shortcut[] getShortcuts(@NonNls String actionId);

  /**
   * @return all actions that have the specified first keystroke. If there are no
   * such actions then the method returns an empty array.
   */
  String[] getActionIds(KeyStroke firstKeyStroke);

  /**
   * @return all actions that have the specified first and second keystrokes. If there are no
   * such actions then the method returns an empty array.
   */
  String[] getActionIds(KeyStroke firstKeyStroke, KeyStroke secondKeyStroke);

  String[] getActionIds(Shortcut shortcut);

  /**
   * @return all actions with specified mouse shortcut.  If there are no
   * such action then the method returns an empty array.
   */
  String[] getActionIds(MouseShortcut shortcut);

  void addShortcut(String actionId, Shortcut shortcut);

  void removeShortcut(String actionId, Shortcut shortcut);

  Map<String, ArrayList<KeyboardShortcut>> getConflicts(String actionId, KeyboardShortcut keyboardShortcut);

  void addShortcutChangeListener(Listener listener);

  void removeShortcutChangeListener(Listener listener);

  void removeAllActionShortcuts(String actionId);

  String[] getAbbreviations();

  void addAbbreviation(String actionId, String abbreviation);

  void removeAbbreviation(String actionId, String abbreviation);

  interface Listener {
    void onShortcutChanged(String actionId);
  }
}