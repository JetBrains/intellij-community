// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.editorHeaderActions;

import com.intellij.find.EditorSearchSession;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;
import org.jetbrains.annotations.NotNull;

public final class AddOccurrenceAction extends OccurrenceAction {
  public AddOccurrenceAction() {
    super(IdeActions.ACTION_SELECT_NEXT_OCCURENCE, AllIcons.Actions.AddMulticaret);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    e.getRequiredData(EditorSearchSession.SESSION_KEY).addNextOccurrence();
  }
}
