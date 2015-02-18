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
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;

import javax.swing.*;

/**
 * This class is here just to be able to assign shortcut to all "refresh"  actions from the keymap.
 * It also serves as a base action for 'refresh' actions (to make dependencies more clear) and
 * provides a convenience method to register its shortcut on a component
 */
public class RefreshAction extends AnAction implements DumbAware {
  public RefreshAction() { }

  public RefreshAction(String text) {
    super(text);
  }

  public RefreshAction(String text, String description, Icon icon) {
    super(text, description, icon);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    // empty
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(false);
  }

  public void registerShortcutOn(JComponent component) {
    final ShortcutSet shortcutSet = ActionManager.getInstance().getAction(IdeActions.ACTION_REFRESH).getShortcutSet();
    registerCustomShortcutSet(shortcutSet, component);
  }
}
