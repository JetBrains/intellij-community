// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.WriteIntentReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;


public final class SaveDocumentAction extends DumbAwareAction {
  /** Returns whether the action is a Save Document action or its registered replacement. */
  public static boolean isSaveDocumentAction(@NotNull AnAction action) {
    return action instanceof SaveDocumentAction || "SaveDocument".equals(ActionManager.getInstance().getId(action));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Document doc = getDocument(e);
    if (doc != null) {
      // sometimes this action is invoked without any lock, like from Driver
      WriteIntentReadAction.run(() -> {
        FileDocumentManager.getInstance().saveDocument(doc);
      });
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(getDocument(e) != null);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  private static Document getDocument(@NotNull AnActionEvent e) {
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    return editor != null ? editor.getDocument() : null;
  }
}
