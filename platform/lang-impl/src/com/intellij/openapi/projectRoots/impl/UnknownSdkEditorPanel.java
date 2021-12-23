// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;
import com.intellij.ui.HyperlinkLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

final class UnknownSdkEditorPanel extends EditorNotificationPanel {

  private static final EditorNotificationProvider PROVIDER = new EditorNotificationProvider() {

    private final Key<? extends JComponent> myKey = Key.create("SdkSetupNotificationNew");

    @Override
    public @NotNull Key<? extends JComponent> getKey() {
      return myKey;
    }

    @Override
    public @NotNull Function<? super @NotNull FileEditor, ? extends @Nullable JComponent> collectNotificationData(@NotNull Project project,
                                                                                                                  @NotNull VirtualFile file) {
      return CONST_NULL;
    }
  };

  private final AtomicBoolean myIsRunning = new AtomicBoolean(false);

  private final @NotNull UnknownSdkFix myFix;
  private final @Nullable UnknownSdkFixAction myAction;

  UnknownSdkEditorPanel(@NotNull Project project,
                        @NotNull FileEditor fileEditor,
                        @NotNull UnknownSdkFix fix) {
    super(fileEditor);
    myFix = fix;
    myAction = myFix.getSuggestedFixAction();

    setProject(project);
    setProvider(PROVIDER);
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
