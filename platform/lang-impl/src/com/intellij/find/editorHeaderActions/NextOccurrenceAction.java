// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.editorHeaderActions;

import com.intellij.find.SearchSession;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class NextOccurrenceAction extends PrevNextOccurrenceAction {
  public NextOccurrenceAction() {
    this(true);
  }

  public NextOccurrenceAction(boolean search) {
    super(IdeActions.ACTION_NEXT_OCCURENCE, search);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    SearchSession session = e.getData(SearchSession.KEY);
    if (session == null) return;
    if (session.hasMatches()) session.searchForward();
  }

  @Override
  protected @NotNull List<Shortcut> getDefaultShortcuts() {
    return Utils.shortcutsOf(IdeActions.ACTION_FIND_NEXT);
  }

  @Override
  protected @NotNull List<Shortcut> getSingleLineShortcuts() {
    if (mySearch) {
      return ContainerUtil.append(Utils.shortcutsOf(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN), CommonShortcuts.ENTER.getShortcuts());
    }
    else {
      return Utils.shortcutsOf(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN);
    }
  }
}
