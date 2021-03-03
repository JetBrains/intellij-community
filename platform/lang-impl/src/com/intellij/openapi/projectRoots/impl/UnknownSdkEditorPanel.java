// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.HyperlinkLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.util.concurrent.atomic.AtomicBoolean;

public final class UnknownSdkEditorPanel extends EditorNotificationPanel {
  private final AtomicBoolean myIsRunning = new AtomicBoolean(false);
  private static final Key<?> EDITOR_NOTIFICATIONS_KEY = Key.create("SdkSetupNotificationNew");

  private final @NotNull UnknownSdkFix myFix;
  private final @Nullable UnknownSdkFixAction myAction;

  public UnknownSdkEditorPanel(@NotNull Project project, @NotNull FileEditor fileEditor, @NotNull UnknownSdkFix fix) {
    super(fileEditor);
    myFix = fix;
    myAction = myFix.getSuggestedFixAction();

    setProject(project);
    setProviderKey(EDITOR_NOTIFICATIONS_KEY);
    setText(fix.getNotificationText());

    if (myAction != null) {
      HyperlinkLabel label = createActionLabel(myAction.getActionShortText(), () -> {
        if (!myIsRunning.compareAndSet(false, true)) return;
        myAction.applySuggestionAsync(project);
      }, true);

      String tooltip = myAction.getActionTooltipText();
      if (tooltip != null) label.setToolTipText(tooltip);
    }

    createActionLabel(myFix.getConfigureActionText(), new EditorNotificationPanel.ActionHandler() {
      private final ActionHandler handler = myFix.getConfigureActionHandler(project);

      @Override
      public void handlePanelActionClick(@NotNull EditorNotificationPanel panel, @NotNull HyperlinkEvent event) {
        if (!myIsRunning.compareAndSet(false, true)) return;
        handler.handlePanelActionClick(panel, event);
      }

      @Override
      public void handleQuickFixClick(@NotNull Editor editor, @NotNull PsiFile psiFile) {
        if (!myIsRunning.compareAndSet(false, true)) return;
        handler.handleQuickFixClick(editor, psiFile);
      }
    }, true);
  }

  @Override
  protected String getIntentionActionText() {
    UnknownSdkFixAction action = myFix.getSuggestedFixAction();
    if (action != null) return action.getActionShortText();
    return myFix.getIntentionActionText();
  }

  @Override
  protected @NotNull PriorityAction.Priority getIntentionActionPriority() {
    return PriorityAction.Priority.HIGH;
  }

  @Override
  protected @NotNull String getIntentionActionFamilyName() {
    return ProjectBundle.message("config.unknown.sdk.configuration");
  }
}
