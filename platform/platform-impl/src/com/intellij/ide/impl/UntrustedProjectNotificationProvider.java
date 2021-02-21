// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.impl;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

public class UntrustedProjectNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> implements DumbAware {

  public static class TrustedListener implements TrustChangeNotifier {
    @Override
    public void projectTrusted(@NotNull Project project) {
      EditorNotifications.getInstance(project).updateAllNotifications();
    }
  }

  public static final Key<EditorNotificationPanel> KEY = Key.create("UntrustedProjectNotification");

  @Override
  public @NotNull Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Override
  public EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file,
                                                         @NotNull FileEditor fileEditor,
                                                         @NotNull Project project) {
    if (TrustedProjects.isTrusted(project)) {
      return null;
    }

    EditorNotificationPanel panel = new EditorNotificationPanel();
    panel.setText(IdeBundle.message("untrusted.project.notification.desctription"));
    panel.createActionLabel(IdeBundle.message("untrusted.project.notification.trust.button"), () -> {
      TrustedProjects.confirmImportingUntrustedProject(project, ApplicationNamesInfo.getInstance().getProductName(),
                                                       IdeBundle.message("untrusted.project.notification.trust.button"),
                                                       CommonBundle.getCancelButtonText());
    }, false);
    return panel;
  }
}
