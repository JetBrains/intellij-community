package com.intellij.openapi.externalSystem.service.remote;

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent;
import org.jetbrains.annotations.NotNull;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Defines interface for the entity that manages notifications about progress of long-running operations performed at Gradle API side.
 * <p/>
 * Implementations of this interface are expected to be thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 11/10/11 9:03 AM
 */
public interface RemoteExternalSystemProgressNotificationManager extends Remote {

  RemoteExternalSystemProgressNotificationManager NULL_OBJECT = new RemoteExternalSystemProgressNotificationManager() {
    @Override
    public void onQueued(@NotNull ExternalSystemTaskId id, @NotNull String projectPath) throws RemoteException {
    }

    @Override
    public void onStart(@NotNull ExternalSystemTaskId id) {
    }

    @Override
    public void onStatusChange(@NotNull ExternalSystemTaskNotificationEvent event) {
    }

    @Override
    public void onTaskOutput(@NotNull ExternalSystemTaskId id, @NotNull String text, boolean stdOut) {
    }

    @Override
    public void onEnd(@NotNull ExternalSystemTaskId id) {
    }

    @Override
    public void onSuccess(@NotNull ExternalSystemTaskId id) throws RemoteException {
    }

    @Override
    public void onFailure(@NotNull ExternalSystemTaskId id, @NotNull Exception e) throws RemoteException {
    }

    @Override
    public void beforeCancel(@NotNull ExternalSystemTaskId id) throws RemoteException {
    }

    @Override
    public void onCancel(ExternalSystemTaskId id) throws RemoteException {
    }
  };

  void onQueued(@NotNull ExternalSystemTaskId id, @NotNull String projectPath) throws RemoteException;

  void onStart(@NotNull ExternalSystemTaskId id) throws RemoteException;

  void onStatusChange(@NotNull ExternalSystemTaskNotificationEvent event) throws RemoteException;

  void onTaskOutput(@NotNull ExternalSystemTaskId id, @NotNull String text, boolean stdOut) throws RemoteException;

  void onEnd(@NotNull ExternalSystemTaskId id) throws RemoteException;

  void onSuccess(@NotNull ExternalSystemTaskId id) throws RemoteException;

  void onFailure(@NotNull ExternalSystemTaskId id, @NotNull Exception e) throws RemoteException;

  void beforeCancel(@NotNull ExternalSystemTaskId id) throws RemoteException;

  void onCancel(ExternalSystemTaskId id) throws RemoteException;
}
