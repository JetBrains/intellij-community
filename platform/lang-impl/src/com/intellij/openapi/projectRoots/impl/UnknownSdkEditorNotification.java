// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

@Service(Service.Level.PROJECT)
public final class UnknownSdkEditorNotification {
  public static @NotNull UnknownSdkEditorNotification getInstance(@NotNull Project project) {
    return project.getService(UnknownSdkEditorNotification.class);
  }

  private final AtomicReference<List<UnknownSdkFix>> notifications = new AtomicReference<>(Collections.emptyList());

  public boolean allowProjectSdkNotifications() {
    return notifications.get().isEmpty();
  }

  public @NotNull List<UnknownSdkFix> getNotifications() {
    return notifications.get();
  }

  public void showNotifications(@NotNull List<? extends UnknownSdkFix> notifications) {
    if (!notifications.isEmpty() && !Registry.is("unknown.sdk.show.editor.actions")) {
      notifications = Collections.emptyList();
    }
    this.notifications.set(List.copyOf(notifications));
  }
}

final class UnknownSdkEditorNotificationsProvider implements EditorNotificationProvider {
  @Override
  public @NotNull Function<? super @NotNull FileEditor, ? extends @Nullable JComponent> collectNotificationData(@NotNull Project project,
                                                                                                                @NotNull VirtualFile file) {
    return editor -> {
      final var sdkService = UnknownSdkEditorNotification.getInstance(project);
      boolean relevantNotifications = false;
      final var panel = new JPanel(new VerticalFlowLayout(0, 0));
      for (UnknownSdkFix info : sdkService.getNotifications()) {
        if (!info.isRelevantFor(project, file)) continue;
        relevantNotifications = true;
        panel.add(new UnknownSdkEditorPanel(project, editor, info));
      }
      return relevantNotifications ? panel : null;
    };
  }
}