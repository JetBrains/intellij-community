package com.intellij.openapi.externalSystem.service.remote;

import com.intellij.execution.process.ProcessOutputType;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Defines interface for the entity that manages notifications about the progress of long-running operations performed at external system API side.
 * <p/>
 * Implementations of this interface are expected to be thread-safe.
 */
@ApiStatus.Internal
public interface RemoteExternalSystemProgressNotificationManager extends Remote {

  RemoteExternalSystemProgressNotificationManager NULL_OBJECT = new RemoteExternalSystemProgressNotificationManager() {
    //@formatter:off
    @Override public void onStart(@NotNull String projectPath, @NotNull ExternalSystemTaskId id) {}
    @Override public void onEnvironmentPrepared(@NotNull ExternalSystemTaskId id) {}
    @Override public void onStatusChange(@NotNull ExternalSystemTaskNotificationEvent event) {}
    @Override public void onTaskOutput(@NotNull ExternalSystemTaskId id, @NotNull String text, @NotNull ProcessOutputType processOutputType) {}
    @Override public void onEnd(@NotNull String projectPath, @NotNull ExternalSystemTaskId id) {}
    @Override public void onSuccess(@NotNull String projectPath, @NotNull ExternalSystemTaskId id) {}
    @Override public void onFailure(@NotNull String projectPath, @NotNull ExternalSystemTaskId id, @NotNull Exception exception) {}
    @Override public void beforeCancel(@NotNull ExternalSystemTaskId id) {}
    @Override public void onCancel(@NotNull String projectPath, @NotNull ExternalSystemTaskId id) {}
    //@formatter:on
  };

  void onStart(@NotNull String projectPath, @NotNull ExternalSystemTaskId id) throws RemoteException;

  void onEnvironmentPrepared(@NotNull ExternalSystemTaskId id) throws RemoteException;

  void onStatusChange(@NotNull ExternalSystemTaskNotificationEvent event) throws RemoteException;

  void onTaskOutput(@NotNull ExternalSystemTaskId id, @NotNull String text, @NotNull ProcessOutputType processOutputType) throws RemoteException;

  void onEnd(@NotNull String projectPath, @NotNull ExternalSystemTaskId id) throws RemoteException;

  void onSuccess(@NotNull String projectPath, @NotNull ExternalSystemTaskId id) throws RemoteException;

  void onFailure(@NotNull String projectPath, @NotNull ExternalSystemTaskId id, @NotNull Exception exception) throws RemoteException;

  void beforeCancel(@NotNull ExternalSystemTaskId id) throws RemoteException;

  void onCancel(@NotNull String projectPath, @NotNull ExternalSystemTaskId id) throws RemoteException;
}
