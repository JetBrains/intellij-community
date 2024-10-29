// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.editorHeaderActions;

import com.intellij.find.SearchSession;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;

public final class PrevOccurrenceAction extends PrevNextOccurrenceAction {
  public PrevOccurrenceAction() {
    this(true);
  }

  public PrevOccurrenceAction(boolean search) {
    super(IdeActions.ACTION_PREVIOUS_OCCURENCE, search);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    SearchSession session = e.getData(SearchSession.KEY);
    if (session == null) return;
    if (session.hasMatches()) session.searchBackward();
  }

  @Override
  protected @NotNull List<Shortcut> getDefaultShortcuts() {
    return Utils.shortcutsOf(IdeActions.ACTION_FIND_PREVIOUS);
  }

  @Override
  protected @NotNull List<Shortcut> getSingleLineShortcuts() {
    if (mySearch) {
      return ContainerUtil.append(Utils.shortcutsOf(IdeActions.ACTION_EDITOR_MOVE_CARET_UP),
                                  new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK), null));
    } else {
      return Utils.shortcutsOf(IdeActions.ACTION_EDITOR_MOVE_CARET_UP);
    }
  }
}
