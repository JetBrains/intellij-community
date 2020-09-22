// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.roots.ui.configuration.UnknownSdk;
import com.intellij.openapi.roots.ui.configuration.UnknownSdkDownloadableSdkFix;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class UnknownSdkEditorNotification {
  public static final Key<List<EditorNotificationPanel>> NOTIFICATIONS = Key.create("notifications added to the editor");

  public static @NotNull UnknownSdkEditorNotification getInstance(@NotNull Project project) {
    return project.getService(UnknownSdkEditorNotification.class);
  }

  private final Project myProject;
  private final FileEditorManager myFileEditorManager;
  private final AtomicReference<Set<UnknownSdkFix>> myNotifications = new AtomicReference<>(new LinkedHashSet<>());

  UnknownSdkEditorNotification(@NotNull Project project) {
    myProject = project;
    myFileEditorManager = FileEditorManager.getInstance(myProject);
    myProject.getMessageBus()
      .connect()
      .subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
        @Override
        public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
          for (FileEditor editor : myFileEditorManager.getEditors(file)) {
            updateEditorNotifications(editor);
          }
        }
      });
  }

  public boolean allowProjectSdkNotifications() {
    return myNotifications.get().isEmpty();
  }

  public @NotNull List<UnknownSdkFix> getNotifications() {
    return ImmutableList.copyOf(myNotifications.get());
  }

  public void showNotifications(@NotNull UnknownSdkEditorNotification.FixableSdkNotification notifications) {
    myNotifications.set(ImmutableSet.copyOf(notifications.getInfos()));
    EditorNotifications.getInstance(myProject).updateAllNotifications();

    ApplicationManager.getApplication().invokeLater(() -> {
      for (FileEditor editor : myFileEditorManager.getAllEditors()) {
        updateEditorNotifications(editor);
      }
    });
  }

  public static final class FixableSdkNotification {
    private final Set<UnknownSdkFix> myInfos;

    private FixableSdkNotification(@NotNull Set<UnknownSdkFix> infos) {
      myInfos = ImmutableSet.copyOf(infos);
    }

    @NotNull
    public Set<UnknownSdkFix> getInfos() {
      return myInfos;
    }

    public boolean isEmpty() {
      return myInfos.isEmpty();
    }
  }

  @NotNull
  public FixableSdkNotification buildNotifications(@NotNull List<UnknownSdk> unfixableSdks,
                                                   @NotNull Map<UnknownSdk, UnknownSdkDownloadableSdkFix> files,
                                                   @NotNull List<UnknownInvalidSdk> invalidSdks) {
    ImmutableSet.Builder<UnknownSdkFix> notifications = ImmutableSet.builder();

    if (Registry.is("unknown.sdk.show.editor.actions")) {
      for (UnknownSdk e : unfixableSdks) {
        @Nullable String name = e.getSdkName();
        SdkType type = e.getSdkType();
        if (name == null) continue;
        notifications.add(new UnknownSdkFixForDownload(myProject, name, type, null, null));
      }

      for (Map.Entry<UnknownSdk, UnknownSdkDownloadableSdkFix> e : files.entrySet()) {
        UnknownSdk unknownSdk = e.getKey();
        String name = unknownSdk.getSdkName();
        if (name == null) continue;

        UnknownSdkDownloadableSdkFix fix = e.getValue();
        notifications.add(new UnknownSdkFixForDownload(myProject, name, unknownSdk.getSdkType(), unknownSdk, fix));
      }

      for (UnknownInvalidSdk sdk : invalidSdks) {
        notifications.add(new UnknownSdkFixForInvalid(myProject, sdk));
      }
    }

    return new FixableSdkNotification(notifications.build());
  }

  private void updateEditorNotifications(@NotNull FileEditor editor) {
    if (!editor.isValid()) return;

    List<EditorNotificationPanel> notifications = editor.getUserData(NOTIFICATIONS);
    if (notifications != null) {
      for (JComponent component : notifications) {
        myFileEditorManager.removeTopComponent(editor, component);
      }
      notifications.clear();
    }
    else {
      notifications = new SmartList<>();
      editor.putUserData(NOTIFICATIONS, notifications);
    }

    for (UnknownSdkFix info : myNotifications.get()) {
      VirtualFile file = editor.getFile();
      if (file == null) continue;

      EditorNotificationPanel notification = info.createNotificationPanel(file, myProject);
      if (notification == null) continue;

      notifications.add(notification);
      myFileEditorManager.addTopComponent(editor, notification);
    }
  }
}
