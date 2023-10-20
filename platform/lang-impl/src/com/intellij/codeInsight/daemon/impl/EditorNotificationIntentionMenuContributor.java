// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.intention.IntentionActionWithOptions;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.ui.EditorNotifications;
import org.jetbrains.annotations.NotNull;

import java.util.List;

final class EditorNotificationIntentionMenuContributor implements IntentionMenuContributor {

  @Override
  public void collectActions(@NotNull Editor hostEditor,
                             @NotNull PsiFile hostFile,
                             ShowIntentionsPass.@NotNull IntentionsInfo intentions,
                             int passIdToShowIntentionsFor,
                             int offset) {
    Project project = hostEditor.getProject();
    if (project == null) return;
    TextEditor fileEditor = TextEditorProvider.getInstance().getTextEditor(hostEditor);
    List<IntentionActionWithOptions> actions = EditorNotifications.getInstance(project).getStoredFileLevelIntentions(fileEditor);
    for (IntentionActionWithOptions action : actions) {
      intentions.notificationActionsToShow.add(new HighlightInfo.IntentionActionDescriptor(
        action, action.getOptions(), null, null, null, null, null));
    }
  }
}
