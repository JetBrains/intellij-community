// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.keymap.impl.KeymapManagerImpl;
import com.intellij.openapi.keymap.impl.KeymapManagerImplKt;
import com.intellij.openapi.keymap.impl.ui.KeymapPanel;
import com.intellij.openapi.keymap.impl.ui.KeymapSchemeManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

public final class QuickChangeKeymapAction extends QuickSwitchSchemeAction implements ActionRemoteBehaviorSpecification.Frontend {
  @Override
  protected void fillActions(Project project, @NotNull DefaultActionGroup group, @NotNull DataContext dataContext) {
    KeymapManagerImpl manager = (KeymapManagerImpl)KeymapManager.getInstance();
    Keymap current = manager.getActiveKeymap();
    List<Keymap> list = ContainerUtil.sorted(getUnsortedKeymaps(),
    KeymapManagerImplKt.getKeymapComparator());
    for (Keymap keymap : list) {
      addKeymapAction(group, manager, current, keymap);
    }
    group.addSeparator();
    group.add(new DumbAwareAction(IdeBundle.message("keymap.action.configure.keymap")) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        ShowSettingsUtil.getInstance().showSettingsDialog(e.getProject(), KeymapPanel.class);
      }
    });
    group.add(new ShowPluginsWithSearchOptionAction(IdeBundle.message("keymap.action.install.keymap"), "/tag:Keymap"));
  }

  private static @Unmodifiable @NotNull List<Keymap> getUnsortedKeymaps() {
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
    // simply show it always instead of asking for keymaps on EDT during update()
    return true;
  }
}
