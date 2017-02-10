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
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.BitUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;

public class CommonShortcuts {

  private CommonShortcuts() {}

  public static final ShortcutSet ALT_ENTER = new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.ALT_DOWN_MASK));
  public static final ShortcutSet ENTER = new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));
  public static final ShortcutSet CTRL_ENTER = new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,
                                                                                            SystemInfo.isMac
                                                                                            ? InputEvent.META_DOWN_MASK
                                                                                            : InputEvent.CTRL_DOWN_MASK));
  public static final ShortcutSet INSERT = new CustomShortcutSet(getInsertKeystroke());

  /**
   * @deprecated use getDelete() instead to support keymap-specific and user-configured shortcuts
   */
  public static final ShortcutSet DELETE = new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0));
  public static final ShortcutSet ESCAPE = new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));

  public static final ShortcutSet DOUBLE_CLICK_1 = new CustomShortcutSet(new MouseShortcut(MouseEvent.BUTTON1, 0, 2));

  public static final ShortcutSet MOVE_UP = CustomShortcutSet.fromString("alt UP");
  public static final ShortcutSet MOVE_DOWN = CustomShortcutSet.fromString("alt DOWN");

  public static ShortcutSet getNewForDialogs() {
    final ArrayList<Shortcut> shortcuts = new ArrayList<>();
    for (Shortcut shortcut : getNew().getShortcuts()) {
      if (isCtrlEnter(shortcut)) continue;
      shortcuts.add(shortcut);
    }
    return new CustomShortcutSet(shortcuts.toArray(new Shortcut[shortcuts.size()]));
  }

  private static boolean isCtrlEnter(Shortcut shortcut) {
    if (shortcut instanceof KeyboardShortcut) {
      KeyStroke keyStroke = ((KeyboardShortcut)shortcut).getFirstKeyStroke();
      return keyStroke.getKeyCode() == KeyEvent.VK_ENTER && BitUtil.isSet(keyStroke.getModifiers(), InputEvent.CTRL_MASK);
    }
    return false;
  }

  public static KeyStroke getInsertKeystroke() {
    return SystemInfo.isMac ? KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK)
                            : KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, 0);
  }

  public static ShortcutSet getCopy() {
    return shortcutsById(IdeActions.ACTION_COPY);
  }

  public static ShortcutSet getPaste() {
    return shortcutsById(IdeActions.ACTION_PASTE);
  }

  public static ShortcutSet getRerun() {
    return shortcutsById(IdeActions.ACTION_RERUN);
  }

  public static ShortcutSet getEditSource() {
    return shortcutsById(IdeActions.ACTION_EDIT_SOURCE);
  }

  public static ShortcutSet getViewSource() {
    return shortcutsById(IdeActions.ACTION_VIEW_SOURCE);
  }

  public static ShortcutSet getNew() {
    return shortcutsById(IdeActions.ACTION_NEW_ELEMENT);
  }

  public static ShortcutSet getDuplicate() {
    return shortcutsById(IdeActions.ACTION_EDITOR_DUPLICATE);
  }

  public static ShortcutSet getMove() {
    return shortcutsById(IdeActions.ACTION_MOVE);
  }

  public static ShortcutSet getRename() {
    return shortcutsById(IdeActions.ACTION_RENAME);
  }

  public static ShortcutSet getDiff() {
    return shortcutsById(IdeActions.ACTION_SHOW_DIFF_COMMON);
  }

  public static ShortcutSet getFind() {
    return shortcutsById(IdeActions.ACTION_FIND);
  }

  public static ShortcutSet getContextHelp() {
    return shortcutsById(IdeActions.ACTION_CONTEXT_HELP);
  }

  public static ShortcutSet getCloseActiveWindow() {
    return shortcutsById(IdeActions.ACTION_CLOSE);
  }

  public static ShortcutSet getMoveUp() {
    return shortcutsById(IdeActions.ACTION_EDITOR_MOVE_CARET_UP);
  }

  public static ShortcutSet getMoveDown() {
    return shortcutsById(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN);
  }

  public static ShortcutSet getMovePageUp() {
    return shortcutsById(IdeActions.ACTION_EDITOR_MOVE_CARET_PAGE_UP);
  }

  public static ShortcutSet getMovePageDown() {
    return shortcutsById(IdeActions.ACTION_EDITOR_MOVE_CARET_PAGE_DOWN);
  }

  public static ShortcutSet getMoveHome() {
    return shortcutsById(IdeActions.ACTION_EDITOR_MOVE_LINE_START);
  }

  public static ShortcutSet getMoveEnd() {
    return shortcutsById(IdeActions.ACTION_EDITOR_MOVE_LINE_END);
  }

  public static ShortcutSet getRecentFiles() {
    return shortcutsById(IdeActions.ACTION_RECENT_FILES);
  }

  public static ShortcutSet getDelete() {
    return shortcutsById(IdeActions.ACTION_DELETE);
  }

  @NotNull
  private static CustomShortcutSet shortcutsById(String actionId) {
    Application application = ApplicationManager.getApplication();
    KeymapManager keymapManager = application == null ? null : application.getComponent(KeymapManager.class);
    if (keymapManager == null) {
      return new CustomShortcutSet(Shortcut.EMPTY_ARRAY);
    }
    return new CustomShortcutSet(keymapManager.getActiveKeymap().getShortcuts(actionId));
  }
}
