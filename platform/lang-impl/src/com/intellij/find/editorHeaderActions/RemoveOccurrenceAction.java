// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.editorHeaderActions;

import com.intellij.find.EditorSearchSession;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;
import org.jetbrains.annotations.NotNull;

public final class RemoveOccurrenceAction extends OccurrenceAction {
  public RemoveOccurrenceAction() {
    super(IdeActions.ACTION_UNSELECT_PREVIOUS_OCCURENCE, AllIcons.Actions.RemoveMulticaret);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    e.getRequiredData(EditorSearchSession.SESSION_KEY).removeOccurrence();
  }
}
