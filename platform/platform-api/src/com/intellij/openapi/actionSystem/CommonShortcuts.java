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

import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.BitUtil;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;

import static com.intellij.openapi.keymap.KeymapUtil.getActiveKeymapShortcuts;

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
  @Deprecated public static final ShortcutSet DELETE = new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0));
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
    return new CustomShortcutSet(shortcuts.toArray(Shortcut.EMPTY_ARRAY));
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
    return getActiveKeymapShortcuts(IdeActions.ACTION_COPY);
  }

  public static ShortcutSet getPaste() {
    return getActiveKeymapShortcuts(IdeActions.ACTION_PASTE);
  }

  public static ShortcutSet getRerun() {
    return getActiveKeymapShortcuts(IdeActions.ACTION_RERUN);
  }

  public static ShortcutSet getEditSource() {
    return getActiveKeymapShortcuts(IdeActions.ACTION_EDIT_SOURCE);
  }

  public static ShortcutSet getViewSource() {
    return getActiveKeymapShortcuts(IdeActions.ACTION_VIEW_SOURCE);
  }

  public static ShortcutSet getNew() {
    return getActiveKeymapShortcuts(IdeActions.ACTION_NEW_ELEMENT);
  }

  public static ShortcutSet getDuplicate() {
    return getActiveKeymapShortcuts(IdeActions.ACTION_EDITOR_DUPLICATE);
  }

  public static ShortcutSet getMove() {
    return getActiveKeymapShortcuts(IdeActions.ACTION_MOVE);
  }

  public static ShortcutSet getRename() {
    return getActiveKeymapShortcuts(IdeActions.ACTION_RENAME);
  }

  public static ShortcutSet getDiff() {
    return getActiveKeymapShortcuts(IdeActions.ACTION_SHOW_DIFF_COMMON);
  }

  public static ShortcutSet getFind() {
    return getActiveKeymapShortcuts(IdeActions.ACTION_FIND);
  }

  public static ShortcutSet getContextHelp() {
    return getActiveKeymapShortcuts(IdeActions.ACTION_CONTEXT_HELP);
  }

  public static ShortcutSet getCloseActiveWindow() {
    return getActiveKeymapShortcuts(IdeActions.ACTION_CLOSE);
  }

  public static ShortcutSet getMoveUp() {
    return getActiveKeymapShortcuts(IdeActions.ACTION_EDITOR_MOVE_CARET_UP);
  }

  public static ShortcutSet getMoveDown() {
    return getActiveKeymapShortcuts(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN);
  }

  public static ShortcutSet getMovePageUp() {
    return getActiveKeymapShortcuts(IdeActions.ACTION_EDITOR_MOVE_CARET_PAGE_UP);
  }

  public static ShortcutSet getMovePageDown() {
    return getActiveKeymapShortcuts(IdeActions.ACTION_EDITOR_MOVE_CARET_PAGE_DOWN);
  }

  public static ShortcutSet getMoveHome() {
    return getActiveKeymapShortcuts(IdeActions.ACTION_EDITOR_MOVE_LINE_START);
  }

  public static ShortcutSet getMoveEnd() {
    return getActiveKeymapShortcuts(IdeActions.ACTION_EDITOR_MOVE_LINE_END);
  }

  public static ShortcutSet getRecentFiles() {
    return getActiveKeymapShortcuts(IdeActions.ACTION_RECENT_FILES);
  }

  public static ShortcutSet getDelete() {
    return getActiveKeymapShortcuts(IdeActions.ACTION_DELETE);
  }
}
