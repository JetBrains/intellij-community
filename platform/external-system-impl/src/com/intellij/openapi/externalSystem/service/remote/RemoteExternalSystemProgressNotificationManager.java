package com.intellij.openapi.externalSystem.service.remote;

import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent;

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
    public void onQueued(@NotNull ExternalSystemTaskId id) throws RemoteException {
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
  };

  void onQueued(@NotNull ExternalSystemTaskId id) throws RemoteException;

  void onStart(@NotNull ExternalSystemTaskId id) throws RemoteException;

  void onStatusChange(@NotNull ExternalSystemTaskNotificationEvent event) throws RemoteException;

  void onTaskOutput(@NotNull ExternalSystemTaskId id, @NotNull String text, boolean stdOut) throws RemoteException;

  void onEnd(@NotNull ExternalSystemTaskId id) throws RemoteException;
}
