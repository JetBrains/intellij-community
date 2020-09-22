// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.roots.ui.configuration.UnknownSdkDownloadableSdkFix;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    return !mySdkType.isRelevantForFile(project, file) ? null : createNotificationPanel(project);
  }

  public final @NotNull EditorNotificationPanel createNotificationPanel(@NotNull Project project) {
    return createNotificationPanelImpl(project);
  }

  protected abstract @NotNull EditorNotificationPanel createNotificationPanelImpl(@NotNull Project project);

  protected @NotNull EditorNotificationPanel newNotificationPanel(@IntentionName @NotNull String intentionActionText) {
    EditorNotificationPanel panel = new EditorNotificationPanel() {
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

    return panel;
  }

  @Nullable
  public abstract DownloadFixAction getDownloadAction();

  public static class DownloadFixAction {
    private final @NotNull UnknownSdkDownloadableSdkFix myFix;

    DownloadFixAction(@NotNull UnknownSdkDownloadableSdkFix fix) {
      myFix = fix;
    }

    public @NotNull @Nls String getActionText() {
      return ProjectBundle.message("config.unknown.sdk.download", myFix.getDownloadDescription());
    }

    @NotNull
    public UnknownSdkDownloadableSdkFix getFix() {
      return myFix;
    }
  }
}
