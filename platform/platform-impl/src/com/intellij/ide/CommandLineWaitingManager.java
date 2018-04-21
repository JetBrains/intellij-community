// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class CommandLineWaitingManager {
  private static final Logger LOG = Logger.getInstance(CommandLineWaitingManager.class);
  
  private final Map<Object, String> myFileOrProjectToTempFile = Collections.synchronizedMap(new HashMap<>());
  
  private CommandLineWaitingManager() {
    final MessageBusConnection busConnection = ApplicationManager.getApplication().getMessageBus().connect();
    
    busConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
      @Override
      public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        freeObject(file);
      }
    });
    busConnection.subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectClosed(Project project) {
        freeObject(project);
      }
    });
  }
  
  @NotNull
  public static CommandLineWaitingManager getInstance() {
    return ServiceManager.getService(CommandLineWaitingManager.class);
  }

  public static void deleteTempFile(@Nullable String tmpFile) {
    if (tmpFile != null) {
      FileUtil.delete(new File(tmpFile));
    }
  }

  public void addHookForFile(@NotNull VirtualFile file, @NotNull String tmpFile) {
    addHookAndNotify(file, tmpFile, "Command line is waiting until the file '" + file.getPath() + "' is closed");
  }

  public void addHookForProject(@NotNull Project project, @NotNull String tmpFile) {
    addHookAndNotify(project, tmpFile, "Command line is waiting until the project '" + project.getName() + "' is closed");
  }

  private void addHookAndNotify(@NotNull Object fileOrProject, 
                                @NotNull String tmpFile,
                                @NotNull String notificationText) {
    LOG.info(notificationText);
    myFileOrProjectToTempFile.put(fileOrProject, tmpFile);
    Notifications.Bus.notify(new Notification(Notifications.SYSTEM_MESSAGES_GROUP_ID,
                                              "Launched from the Command Line",
                                              notificationText,
                                              NotificationType.WARNING).setImportant(true));
  }

  private void freeObject(@NotNull Object fileOrProject) {
    final String tmpFile = myFileOrProjectToTempFile.remove(fileOrProject);
    if (tmpFile == null) {
      return;
    }

    deleteTempFile(tmpFile);
  }
}
