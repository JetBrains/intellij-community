// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.intention.IntentionActionWithOptions;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.ui.EditorNotificationsImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.Internal
public final class EditorNotificationActions {
  public static void collectActions(@NotNull Editor hostEditor, @NotNull ShowIntentionsPass.IntentionsInfo intentions) {
    Project project = hostEditor.getProject();
    if (project == null) return;
    TextEditor fileEditor = TextEditorProvider.getInstance().getTextEditor(hostEditor);
    List<IntentionActionWithOptions> actions = EditorNotificationsImpl.getInstance(project).getStoredFileLevelIntentions(fileEditor);
    for (IntentionActionWithOptions action : actions) {
      intentions.notificationActionsToShow.add(new HighlightInfo.IntentionActionDescriptor(action, action.getOptions(), null, null, null, null, null));
    }
  }
}
