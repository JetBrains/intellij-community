package com.intellij.openapi.externalSystem.service.remote.wrapper;

import com.intellij.openapi.externalSystem.model.settings.ExternalSystemExecutionSettings;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.externalSystem.service.RemoteExternalSystemFacade;
import com.intellij.openapi.externalSystem.service.remote.RemoteExternalSystemProgressNotificationManager;
import com.intellij.openapi.externalSystem.service.remote.RemoteExternalSystemProjectResolver;
import com.intellij.openapi.externalSystem.service.remote.RemoteExternalSystemTaskManager;
import org.jetbrains.annotations.NotNull;

import java.rmi.RemoteException;
import java.util.Map;
import java.util.Set;

/**
 * This class acts as a point where target remote gradle services are proxied.
 * <p/>
 * Check service wrapper contracts for more details.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 2/8/12 7:21 PM
 */
public class ExternalSystemFacadeWrapper<S extends ExternalSystemExecutionSettings> implements RemoteExternalSystemFacade<S> {

  @NotNull private final RemoteExternalSystemFacade<S>                   myDelegate;
  @NotNull private final RemoteExternalSystemProgressNotificationManager myProgressManager;

  public ExternalSystemFacadeWrapper(@NotNull RemoteExternalSystemFacade<S> delegate,
                                     @NotNull RemoteExternalSystemProgressNotificationManager progressManager)
  {
    myDelegate = delegate;
    myProgressManager = progressManager;
  }

  @NotNull
  public RemoteExternalSystemFacade<S> getDelegate() {
    return myDelegate;
  }

  @NotNull
  @Override
  public RemoteExternalSystemProjectResolver<S> getResolver() throws RemoteException, IllegalStateException {
    return new ExternalSystemProjectResolverWrapper<S>(myDelegate.getResolver(), myProgressManager);
  }

  @NotNull
  @Override
  public RemoteExternalSystemTaskManager<S> getTaskManager() throws RemoteException {
    return new ExternalSystemTaskManagerWrapper<S>(myDelegate.getTaskManager(), myProgressManager);
  }

  @Override
  public void applySettings(@NotNull S settings) throws RemoteException {
    myDelegate.applySettings(settings);
  }

  @Override
  public void applyProgressManager(@NotNull RemoteExternalSystemProgressNotificationManager progressManager) throws RemoteException {
    myDelegate.applyProgressManager(progressManager);
  }

  @Override
  public boolean isTaskInProgress(@NotNull ExternalSystemTaskId id) throws RemoteException {
    return myDelegate.isTaskInProgress(id);
  }

  @NotNull
  @Override
  public Map<ExternalSystemTaskType, Set<ExternalSystemTaskId>> getTasksInProgress() throws RemoteException {
    return myDelegate.getTasksInProgress();
  }

  @Override
  public boolean cancelTask(@NotNull ExternalSystemTaskId id) throws RemoteException {
    return myDelegate.cancelTask(id);
  }
}
