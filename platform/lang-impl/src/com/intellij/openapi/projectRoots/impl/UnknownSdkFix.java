// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationPanel.ActionHandler;
import com.intellij.ui.HyperlinkLabel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class UnknownSdkFix {
  private static final Key<?> EDITOR_NOTIFICATIONS_KEY = Key.create("SdkSetupNotificationNew");

  protected final @NotNull Project myProject;
  protected final @NotNull SdkType mySdkType;

  protected UnknownSdkFix(@NotNull Project project, @NotNull SdkType sdkType) {
    myProject = project;
    mySdkType = sdkType;
  }

  public final @Nullable EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file, @NotNull Project project) {
    // we must not show the notification for an irrelevant files in the project
    return !mySdkType.isRelevantForFile(project, file) || project != myProject ? null : createNotificationPanelImpl().panel;
  }

  protected abstract @NotNull EditorNotificationPanelWrapper createNotificationPanelImpl();

  protected class EditorNotificationPanelWrapper {
    private final EditorNotificationPanel panel;
    private final AtomicBoolean myIsRunning = new AtomicBoolean(false);

    public EditorNotificationPanelWrapper(@IntentionName @NotNull String intentionActionText) {
      panel = new EditorNotificationPanel() {
        @Override
        protected String getIntentionActionText() {
          return intentionActionText;
        }

        @Override
        protected @NotNull PriorityAction.Priority getIntentionActionPriority() {
          return PriorityAction.Priority.HIGH;
        }

        @Override
        protected @NotNull String getIntentionActionFamilyName() {
          return ProjectBundle.message("config.unknown.sdk.configuration");
        }
      };
      panel.setProject(myProject);
      panel.setProviderKey(EDITOR_NOTIFICATIONS_KEY);
      panel.setText(getNotificationText());
    }

    @NotNull
    public EditorNotificationPanelWrapper createActionLabel(@NlsContexts.LinkLabel String text, @NotNull final ActionHandler action) {
      panel.createActionLabel(text, new ActionHandler() {
        @Override
        public void handlePanelActionClick(@NotNull EditorNotificationPanel panel, @NotNull HyperlinkEvent event) {
          if (!myIsRunning.compareAndSet(false, true)) return;
          action.handlePanelActionClick(panel, event);
        }

        @Override
        public void handleQuickFixClick(@NotNull Editor editor, @NotNull PsiFile psiFile) {
          if (!myIsRunning.compareAndSet(false, true)) return;
          action.handleQuickFixClick(editor, psiFile);
        }
      }, true);
      return this;
    }

    @NotNull
    private EditorNotificationPanelWrapper attachAction(@NotNull UnknownSdkFixAction fixAction) {
      HyperlinkLabel label = panel.createActionLabel(fixAction.getActionText(), () -> {
        if (!myIsRunning.compareAndSet(false, true)) return;
        fixAction.applySuggestionAsync();
      }, true);
      String tooltip = fixAction.getCheckboxActionTooltip();
      if (tooltip != null) label.setToolTipText(tooltip);
      return this;
    }
  }

  protected @NotNull EditorNotificationPanelWrapper newNotificationPanel(@IntentionName @NotNull String intentionActionText) {
    return new EditorNotificationPanelWrapper(intentionActionText);
  }

  @NotNull
  protected EditorNotificationPanelWrapper createNotificationPanelWithMainAction(@NotNull UnknownSdkFixAction fixAction) {
    var notification = newNotificationPanel(fixAction.getActionText());
    return notification.attachAction(fixAction);
  }

  @Nls
  @NotNull
  public abstract String getNotificationText();

  @Nls
  @Nullable
  public abstract String getSdkTypeAndNameText();

  @Nullable
  public abstract UnknownSdkFixAction getSuggestedFixAction();
}
