// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.ProjectSdkSetupValidator;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.impl.UnknownSdkEditorNotification;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationPanel.ActionHandler;
import com.intellij.ui.EditorNotificationProvider;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Function;

@ApiStatus.Internal
public final class SdkSetupNotificationProvider implements EditorNotificationProvider, DumbAware {
  @Override
  public @Nullable Function<? super @NotNull FileEditor, ? extends @Nullable JComponent> collectNotificationData(@NotNull Project project,
                                                                                                                 @NotNull VirtualFile file) {
    if (!UnknownSdkEditorNotification.getInstance(project).allowProjectSdkNotifications()) {
      return null;
    }

    for (ProjectSdkSetupValidator validator : ProjectSdkSetupValidator.EP_NAME.getExtensionList()) {
      if (validator.isApplicableFor(project, file)) {
        String errorMessage = validator.getErrorMessage(project, file);
        return errorMessage == null ? null : fileEditor -> createPanel(errorMessage, fileEditor, validator.getFixHandler(project, file));
      }
    }

    return null;
  }

  @RequiresEdt
  private static @NotNull EditorNotificationPanel createPanel(@NotNull @NlsContexts.LinkLabel String message,
                                                              @NotNull FileEditor fileEditor,
                                                              @NotNull ActionHandler fix) {
    EditorNotificationPanel panel = new EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Warning);
    panel.setText(message);
    panel.createActionLabel(ProjectBundle.message("project.sdk.setup"), fix, true);
    return panel;
  }
}
