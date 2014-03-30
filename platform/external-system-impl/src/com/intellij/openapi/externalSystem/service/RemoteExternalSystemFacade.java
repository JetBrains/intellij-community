package com.intellij.openapi.externalSystem.service;

import com.intellij.openapi.externalSystem.model.settings.ExternalSystemExecutionSettings;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemTaskAware;
import com.intellij.openapi.externalSystem.service.remote.RemoteExternalSystemProgressNotificationManager;
import com.intellij.openapi.externalSystem.service.remote.RemoteExternalSystemProjectResolver;
import com.intellij.openapi.externalSystem.service.remote.RemoteExternalSystemTaskManager;
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
 * 
 * @author Denis Zhdanov
 * @since 8/8/11 10:52 AM
 */
public interface RemoteExternalSystemFacade<S extends ExternalSystemExecutionSettings> extends Remote, ExternalSystemTaskAware {

  /** <a href="http://en.wikipedia.org/wiki/Null_Object_pattern">Null object</a> for {@link RemoteExternalSystemFacade}. */
  RemoteExternalSystemFacade<?> NULL_OBJECT = new RemoteExternalSystemFacade<ExternalSystemExecutionSettings>() {
    @NotNull
    @Override
    public RemoteExternalSystemProjectResolver<ExternalSystemExecutionSettings> getResolver()
      throws RemoteException, IllegalStateException
    {
      return RemoteExternalSystemProjectResolver.NULL_OBJECT;
    }


    @NotNull
    @Override
    public RemoteExternalSystemTaskManager<ExternalSystemExecutionSettings> getTaskManager() throws RemoteException {
      return RemoteExternalSystemTaskManager.NULL_OBJECT;
    }

    @Override
    public void applySettings(@NotNull ExternalSystemExecutionSettings settings) throws RemoteException {
    }

    @Override
    public void applyProgressManager(@NotNull RemoteExternalSystemProgressNotificationManager progressManager) throws RemoteException {
    }

    @Override
    public boolean isTaskInProgress(@NotNull ExternalSystemTaskId id) throws RemoteException {
      return false;
    }

    @Override
    public boolean cancelTask(@NotNull ExternalSystemTaskId id) throws RemoteException {
      return false;
    }

    @NotNull
    @Override
    public Map<ExternalSystemTaskType, Set<ExternalSystemTaskId>> getTasksInProgress() throws RemoteException {
      return Collections.emptyMap();
    }
  };

  /**
   * Exposes <code>'resolve external system project'</code> service that works at another process.
   *
   * @return                        <code>'resolve external system project'</code> service
   * @throws RemoteException        in case of unexpected I/O exception during processing
   * @throws IllegalStateException  in case of inability to create the service
   */
  @NotNull
  RemoteExternalSystemProjectResolver<S> getResolver() throws RemoteException, IllegalStateException;

  /**
   * Exposes <code>'run external system task'</code> service which works at another process.
   *
   * @return external system build manager
   * @throws RemoteException  in case of inability to create the service
   */
  @NotNull
  RemoteExternalSystemTaskManager<S> getTaskManager() throws RemoteException;

  /**
   * Asks remote external system process to apply given settings.
   *
   * @param settings            settings to apply
   * @throws RemoteException    in case of unexpected I/O exception during processing
   */
  void applySettings(@NotNull S settings) throws RemoteException;

  /**
   * Asks remote external system process to use given progress manager.
   *
   * @param progressManager  progress manager to use
   * @throws RemoteException    in case of unexpected I/O exception during processing
   */
  void applyProgressManager(@NotNull RemoteExternalSystemProgressNotificationManager progressManager) throws RemoteException;
}
