// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.editorHeaderActions;

import com.intellij.find.EditorSearchSession;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.ui.ExperimentalUI;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class SelectAllAction extends OccurrenceAction {

  public SelectAllAction() {
    super(IdeActions.ACTION_SELECT_ALL_OCCURRENCES, AllIcons.Actions.CheckMulticaret);
  }

  @Override
  protected boolean availableForReplace() {
    return true;
  }

  @Override
  protected boolean availableForSelection() {
    return true;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    EditorSearchSession session = e.getData(EditorSearchSession.SESSION_KEY);
    if (session == null) return;
    session.selectAllOccurrences();
    session.close();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    if (ExperimentalUI.isNewUI()) {
      e.getPresentation().setIcon(null);
    }
  }

  @Override
  public @NotNull ShortcutSet getShortcut() {
    List<Shortcut> shortcuts = new ArrayList<>();
    AnAction selectAllOccurrences = ActionManager.getInstance().getAction(IdeActions.ACTION_SELECT_ALL_OCCURRENCES);
    if (selectAllOccurrences != null) {
      ContainerUtil.addAll(shortcuts, selectAllOccurrences.getShortcutSet().getShortcuts());
    }
    ContainerUtil.addAll(shortcuts, CommonShortcuts.ALT_ENTER.getShortcuts());
    return Utils.shortcutSetOf(shortcuts);
  }
}
