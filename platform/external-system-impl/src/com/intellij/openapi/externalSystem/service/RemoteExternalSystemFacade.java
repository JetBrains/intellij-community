package com.intellij.openapi.externalSystem.service;

import com.intellij.openapi.externalSystem.model.settings.ExternalSystemExecutionSettings;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemTaskAware;
import com.intellij.openapi.externalSystem.service.remote.RawExternalSystemProjectResolver;
import com.intellij.openapi.externalSystem.service.remote.RemoteExternalSystemProgressNotificationManager;
import com.intellij.openapi.externalSystem.service.remote.RemoteExternalSystemProjectResolver;
import com.intellij.openapi.externalSystem.service.remote.RemoteExternalSystemTaskManager;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.externalSystem.util.task.TaskExecutionSpec;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Serves as a facade for working with external system which might be located at an external (non-ide) process.
 * <p/>
 * The main idea is that we don't want to use it directly from an ide process (to avoid unnecessary heap/perm gen pollution,
 * memory leaks etc). So, we use it at external process and current class works as a facade to it from ide process.
 */
@ApiStatus.NonExtendable
public interface RemoteExternalSystemFacade<S extends ExternalSystemExecutionSettings> extends Remote, ExternalSystemTaskAware {

  @ApiStatus.Internal
  RemoteExternalSystemFacade<?> NULL_OBJECT = new RemoteExternalSystemFacade<>() {

    @Override
    public @NotNull RemoteExternalSystemProjectResolver<ExternalSystemExecutionSettings> getResolver() throws IllegalStateException {
      return RemoteExternalSystemProjectResolver.NULL_OBJECT;
    }

    @Override
    public @NotNull RemoteExternalSystemTaskManager<ExternalSystemExecutionSettings> getTaskManagerImpl() {
      return RemoteExternalSystemTaskManager.NULL_OBJECT;
    }

    @Override
    public void applySettings(@NotNull ExternalSystemExecutionSettings settings) { }

    @Override
    public void applyProgressManager(@NotNull RemoteExternalSystemProgressNotificationManager progressManager) { }

    @Override
    public @NotNull RawExternalSystemProjectResolver<ExternalSystemExecutionSettings> getRawProjectResolver() {
      return RawExternalSystemProjectResolver.Companion.getNULL_OBJECT();
    }

    @Override
    public boolean isTaskInProgress(@NotNull ExternalSystemTaskId id) {
      return false;
    }

    @Override
    public boolean cancelTask(@NotNull ExternalSystemTaskId id) {
      return false;
    }

    @Override
    public @NotNull Map<ExternalSystemTaskType, Set<ExternalSystemTaskId>> getTasksInProgress() {
      return Collections.emptyMap();
    }
  };

  /**
   * Exposes {@code 'resolve external system project'} service that works at another process.
   *
   * @return {@code 'resolve external system project'} service
   * @throws RemoteException       in case of unexpected I/O exception during processing
   * @throws IllegalStateException in case of inability to create the service
   */
  @ApiStatus.Internal
  @NotNull RemoteExternalSystemProjectResolver<S> getResolver() throws RemoteException, IllegalStateException;

  /**
   * Exposes {@code 'run external system task'} service which works at another process.
   *
   * @return external system build manager
   * @throws RemoteException in case of inability to create the service
   * @deprecated {@link RemoteExternalSystemTaskManager} should never be used in favor of
   * {@link ExternalSystemUtil#runTask(TaskExecutionSpec)}
   */
  @Deprecated(forRemoval = true)
  default @NotNull RemoteExternalSystemTaskManager<S> getTaskManager() throws RemoteException {
    return getTaskManagerImpl();
  }

  @ApiStatus.Internal
  @NotNull RemoteExternalSystemTaskManager<S> getTaskManagerImpl() throws RemoteException;

  /**
   * Asks remote external system process to apply given settings.
   *
   * @param settings settings to apply
   * @throws RemoteException in case of unexpected I/O exception during processing
   */
  @ApiStatus.Internal
  void applySettings(@NotNull S settings) throws RemoteException;

  /**
   * Asks remote external system process to use given progress manager.
   *
   * @param progressManager progress manager to use
   * @throws RemoteException in case of unexpected I/O exception during processing
   */
  @ApiStatus.Internal
  void applyProgressManager(@NotNull RemoteExternalSystemProgressNotificationManager progressManager) throws RemoteException;

  /**
   * Same as {@link #getResolver()}, but operating on raw result
   */
  @ApiStatus.Internal
  @NotNull RawExternalSystemProjectResolver<S> getRawProjectResolver() throws RemoteException;
}
