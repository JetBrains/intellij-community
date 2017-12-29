// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class QuickChangeKeymapAction extends QuickSwitchSchemeAction {
  @Override
  protected void fillActions(Project project, @NotNull DefaultActionGroup group, @NotNull DataContext dataContext) {
    KeymapManagerEx manager = (KeymapManagerEx) KeymapManager.getInstance();
    Keymap current = manager.getActiveKeymap();
    for (Keymap keymap : manager.getAllKeymaps()) {
      addKeymapAction(group, manager, current, keymap, false);
    }
  }

  private static void addKeymapAction(final DefaultActionGroup group, final KeymapManagerEx manager, final Keymap current, final Keymap keymap, final boolean addScheme) {
    group.add(new DumbAwareAction(keymap.getPresentableName(), "", keymap == current ? ourCurrentAction : ourNotCurrentAction) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        if (addScheme) {
          manager.getSchemeManager().addScheme(keymap, false);
        }
        manager.setActiveKeymap(keymap);
      }
    });
  }

  @Override
  protected boolean isEnabled() {
    return ((KeymapManagerEx) KeymapManager.getInstance()).getAllKeymaps().length > 1;
  }
}
