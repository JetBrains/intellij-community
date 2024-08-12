// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.editorHeaderActions;

import com.intellij.find.FindBundle;
import com.intellij.find.SearchSession;
import com.intellij.icons.AllIcons;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ToggleFindInSelectionAction extends ToggleAction implements ContextAwareShortcutProvider, DumbAware,
                                                                               LightEditCompatible,
                                                                               ActionRemoteBehaviorSpecification.Frontend {
  public ToggleFindInSelectionAction() {
    super(FindBundle.message("find.selection.only"), null, AllIcons.Actions.InSelection);

    getTemplatePresentation().setKeepPopupOnPerform(KeepPopupOnPerform.IfRequested);
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    SearchSession search = e.getData(SearchSession.KEY);
    return search != null && !search.getFindModel().isGlobal();
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    e.getPresentation().setEnabled(e.getData(SearchSession.KEY) != null);
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    SearchSession search = e.getData(SearchSession.KEY);
    if (search != null) {
      search.getFindModel().setGlobal(!state);
    }
  }

  @Override
  public @Nullable ShortcutSet getShortcut(@NotNull DataContext context) {
    if (KeymapUtil.isEmacsKeymap()) return null;
    SearchSession search = context.getData(SearchSession.KEY);
    if (search != null) {
      boolean replaceState = search.getFindModel().isReplaceState() &&
                             !Registry.is("ide.find.use.search.in.selection.keyboard.shortcut.for.replace");
      AnAction action = ActionManager.getInstance().getAction(
        replaceState ? IdeActions.ACTION_REPLACE : IdeActions.ACTION_TOGGLE_FIND_IN_SELECTION_ONLY);
      return action != null ? action.getShortcutSet() : null;
    }
    return null;
  }
}
