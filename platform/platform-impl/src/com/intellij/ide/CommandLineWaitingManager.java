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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public final class CommandLineWaitingManager {
  private static final Logger LOG = Logger.getInstance(CommandLineWaitingManager.class);
  
  private final Map<Object, CompletableFuture<CliResult>> myFileOrProjectToCallback = Collections.synchronizedMap(new HashMap<>());
  
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
      public void projectClosed(@NotNull Project project) {
        freeObject(project);
      }
    });
  }
  
  @NotNull
  public static CommandLineWaitingManager getInstance() {
    return ServiceManager.getService(CommandLineWaitingManager.class);
  }

  @NotNull
  public Future<CliResult> addHookForFile(@NotNull VirtualFile file) {
    return addHookAndNotify(file, "Command line is waiting until the file '" + file.getPath() + "' is closed");
  }

  @NotNull
  public Future<CliResult> addHookForProject(@NotNull Project project) {
    return addHookAndNotify(project, "Command line is waiting until the project '" + project.getName() + "' is closed");
  }
  
  @NotNull
  private Future<CliResult> addHookAndNotify(@NotNull Object fileOrProject,
                                             @NotNull String notificationText) {
    LOG.info(notificationText);

    final CompletableFuture<CliResult> result = new CompletableFuture<>();
    myFileOrProjectToCallback.put(fileOrProject, result);
    Notifications.Bus.notify(new Notification(Notifications.SYSTEM_MESSAGES_GROUP_ID,
                                              "Launched from the Command Line",
                                              notificationText,
                                              NotificationType.WARNING).setImportant(true));
    
    return result;
  }

  private void freeObject(@NotNull Object fileOrProject) {
    final CompletableFuture<CliResult> future = myFileOrProjectToCallback.remove(fileOrProject);
    if (future == null) {
      return;
    }

    future.complete(new CliResult(0, null));
  }
}
