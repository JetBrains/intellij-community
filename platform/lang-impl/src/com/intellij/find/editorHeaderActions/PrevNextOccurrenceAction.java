// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.editorHeaderActions;

import com.intellij.find.SearchSession;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class PrevNextOccurrenceAction extends DumbAwareAction implements ContextAwareShortcutProvider,
                                                                                  LightEditCompatible {
  protected final boolean mySearch;

  public PrevNextOccurrenceAction(@NotNull String templateActionId, boolean search) {
    mySearch = search;
    ActionUtil.copyFrom(this, templateActionId);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    SearchSession search = e.getData(SearchSession.KEY);
    e.getPresentation().setEnabled(search != null && !search.isSearchInProgress() && search.hasMatches());
  }

  @Override
  public final ShortcutSet getShortcut(@NotNull DataContext context) {
    SearchSession search = SearchSession.KEY.getData(context);
    boolean singleLine = search != null && !search.getFindModel().isMultiline();
    return Utils.shortcutSetOf(singleLine ? ContainerUtil.concat(getDefaultShortcuts(), getSingleLineShortcuts()) : getDefaultShortcuts());
  }

  protected abstract @NotNull List<Shortcut> getDefaultShortcuts();

  protected abstract @NotNull List<Shortcut> getSingleLineShortcuts();
}
