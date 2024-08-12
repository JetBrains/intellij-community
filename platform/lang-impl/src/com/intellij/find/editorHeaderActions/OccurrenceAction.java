// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.editorHeaderActions;

import com.intellij.find.EditorSearchSession;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ShortcutProvider;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class OccurrenceAction extends DumbAwareAction implements ShortcutProvider, LightEditCompatible {
  protected OccurrenceAction(@NotNull String baseActionId, @NotNull Icon icon) {
    ActionUtil.copyFrom(this, baseActionId);
    getTemplatePresentation().setIcon(icon);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    EditorSearchSession search = e.getData(EditorSearchSession.SESSION_KEY);
    if (search == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    boolean visible = !search.getFindModel().isReplaceState() || availableForReplace();
    boolean hasMatches = search.hasMatches();
    e.getPresentation().setVisible(visible);
    e.getPresentation().setEnabled(visible && hasMatches && (availableForSelection() || search.getFindModel().isGlobal()));
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  protected boolean availableForReplace() {
    return false;
  }

  protected boolean availableForSelection() {
    return false;
  }

  @Override
  public @Nullable ShortcutSet getShortcut() {
    return getShortcutSet();
  }
}
