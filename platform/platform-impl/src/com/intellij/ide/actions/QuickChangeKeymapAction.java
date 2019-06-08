// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.keymap.impl.KeymapManagerImpl;
import com.intellij.openapi.keymap.impl.KeymapManagerImplKt;
import com.intellij.openapi.keymap.impl.ui.KeymapSchemeManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class QuickChangeKeymapAction extends QuickSwitchSchemeAction {
  @Override
  protected void fillActions(Project project, @NotNull DefaultActionGroup group, @NotNull DataContext dataContext) {
    KeymapManagerImpl manager = (KeymapManagerImpl)KeymapManager.getInstance();
    Keymap current = manager.getActiveKeymap();
    List<Keymap> list = getUnsortedKeymaps();
    list.sort(KeymapManagerImplKt.getKeymapComparator());
    for (Keymap keymap : list) {
      addKeymapAction(group, manager, current, keymap);
    }
  }

  @NotNull
  private static List<Keymap> getUnsortedKeymaps() {
    return ((KeymapManagerImpl)KeymapManager.getInstance()).getKeymaps(KeymapSchemeManager.FILTER);
  }

  private static void addKeymapAction(@NotNull DefaultActionGroup group, @NotNull KeymapManagerEx manager, @Nullable Keymap current, @NotNull Keymap keymap) {
    group.add(new DumbAwareAction(keymap.getPresentableName(), "", keymap == current ? AllIcons.Actions.Forward : ourNotCurrentAction) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        manager.setActiveKeymap(keymap);
      }
    });
  }

  @Override
  protected boolean isEnabled() {
    return getUnsortedKeymaps().size() > 1;
  }
}
