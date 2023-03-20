// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service;

import com.intellij.execution.rmi.RemoteServer;
import com.intellij.openapi.externalSystem.model.settings.ExternalSystemExecutionSettings;
import com.intellij.openapi.externalSystem.model.task.*;
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver;
import com.intellij.openapi.externalSystem.service.remote.*;
import com.intellij.openapi.externalSystem.task.ExternalSystemTaskManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

public abstract class AbstractExternalSystemFacadeImpl<S extends ExternalSystemExecutionSettings> extends RemoteServer
  implements RemoteExternalSystemFacade<S>
{

  private final ConcurrentMap<Class<?>, RemoteExternalSystemService<S>> myRemotes = new ConcurrentHashMap<>();

  private final AtomicReference<S> mySettings              = new AtomicReference<>();
  private final AtomicReference<ExternalSystemTaskNotificationListener> myNotificationListener =
    new AtomicReference<>(new ExternalSystemTaskNotificationListenerAdapter() {
    });

  private final @NotNull RemoteExternalSystemProjectResolverImpl<S> myProjectResolver;
  private final @NotNull RemoteExternalSystemTaskManagerImpl<S>     myTaskManager;

  public AbstractExternalSystemFacadeImpl(@NotNull Class<ExternalSystemProjectResolver<S>> projectResolverClass,
                                          @NotNull Class<ExternalSystemTaskManager<S>> buildManagerClass)
    throws IllegalAccessException, InstantiationException {
    try {
      myProjectResolver = new RemoteExternalSystemProjectResolverImpl<>(projectResolverClass.getConstructor().newInstance());
      myTaskManager = new RemoteExternalSystemTaskManagerImpl<>(buildManagerClass.getConstructor().newInstance());
    }
    catch (InvocationTargetException | NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  protected void init() throws RemoteException {
    applyProgressManager(RemoteExternalSystemProgressNotificationManager.NULL_OBJECT);
  }

  protected @Nullable S getSettings() {
    return mySettings.get();
  }

  protected @NotNull ExternalSystemTaskNotificationListener getNotificationListener() {
    return myNotificationListener.get();
  }

  @SuppressWarnings("unchecked")
  @Override
  public @NotNull RemoteExternalSystemProjectResolver<S> getResolver() throws IllegalStateException {
    try {
      return getService(RemoteExternalSystemProjectResolver.class, myProjectResolver);
    }
    catch (Exception e) {
      throw new IllegalStateException(String.format("Can't create '%s' service", RemoteExternalSystemProjectResolverImpl.class.getName()),
                                      e);
    }
  }

  @Override
  public @NotNull RawExternalSystemProjectResolver<S> getRawProjectResolver() throws IllegalStateException {
    try {
      return getService(RawExternalSystemProjectResolver.class, new RawExternalSystemProjectResolverImpl<>(myProjectResolver));
    }
    catch (Exception e) {
      throw new IllegalStateException(String.format("Can't create '%s' service", RawExternalSystemProjectResolverImpl.class.getName()),
                                      e);
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public @NotNull RemoteExternalSystemTaskManager<S> getTaskManager() {
    try {
      return getService(RemoteExternalSystemTaskManager.class, myTaskManager);
    }
    catch (Exception e) {
      throw new IllegalStateException(String.format("Can't create '%s' service", ExternalSystemTaskManager.class.getName()), e);
    }
  }

  @SuppressWarnings("unchecked")
  private <I extends RemoteExternalSystemService<S>, C extends I> I getService(@NotNull Class<I> interfaceClass,
                                                                               final @NotNull C impl)
    throws ClassNotFoundException, IllegalAccessException, InstantiationException, RemoteException
  {
    Object cachedResult = myRemotes.get(interfaceClass);
    if (cachedResult != null) {
      return (I)cachedResult;
    }
    S settings = getSettings();
    if (settings != null) {
      impl.setNotificationListener(getNotificationListener());
      impl.setSettings(settings);
    }
    impl.setNotificationListener(getNotificationListener());
    try {
      I created = createService(interfaceClass, impl);
      I stored = (I)myRemotes.putIfAbsent(interfaceClass, created);
      return stored == null ? created : stored;
    }
    catch (RemoteException e) {
      Object raceResult = myRemotes.get(interfaceClass);
      if (raceResult != null) {
        // Race condition occurred
        return (I)raceResult;
      }
      else {
        throw new IllegalStateException(
          String.format("Can't prepare remote service for interface '%s', implementation '%s'", interfaceClass, impl),
          e
        );
      }
    }
  }

  /**
   * Generic method to retrieve exposed implementations of the target interface.
   * <p/>
   * Uses cached value if it's found; creates new and caches it otherwise.
   *
   * @param interfaceClass  target service interface class
   * @param impl            service implementation
   * @param <I>             service interface class
   * @param <C>             service implementation
   * @return implementation of the target service
   * @throws IllegalAccessException   in case of incorrect assumptions about server class interface
   * @throws InstantiationException   in case of incorrect assumptions about server class interface
   * @throws ClassNotFoundException   in case of incorrect assumptions about server class interface
   */
  protected abstract  <I extends RemoteExternalSystemService<S>, C extends I> I createService(@NotNull Class<I> interfaceClass,
                                                                                              final @NotNull C impl)
  throws ClassNotFoundException, IllegalAccessException, InstantiationException, RemoteException;

  @Override
  public boolean isTaskInProgress(@NotNull ExternalSystemTaskId id) throws RemoteException {
    for (RemoteExternalSystemService<?> service : myRemotes.values()) {
      if (service.isTaskInProgress(id)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public @NotNull Map<ExternalSystemTaskType, Set<ExternalSystemTaskId>> getTasksInProgress() throws RemoteException {
    Map<ExternalSystemTaskType, Set<ExternalSystemTaskId>> result = null;
    for (RemoteExternalSystemService<?> service : myRemotes.values()) {
      final Map<ExternalSystemTaskType, Set<ExternalSystemTaskId>> tasks = service.getTasksInProgress();
      if (tasks.isEmpty()) {
        continue;
      }
      if (result == null) {
        result = new HashMap<>();
      }
      for (Map.Entry<ExternalSystemTaskType, Set<ExternalSystemTaskId>> entry : tasks.entrySet()) {
        Set<ExternalSystemTaskId> ids = result.get(entry.getKey());
        if (ids == null) {
          result.put(entry.getKey(), ids = new HashSet<>());
        }
        ids.addAll(entry.getValue());
      }
    }
    if (result == null) {
      result = Collections.emptyMap();
    }
    return result;
  }

  @Override
  public void applySettings(@NotNull S settings) throws RemoteException {
    mySettings.set(settings);
    List<RemoteExternalSystemService<S>> services =
      new ArrayList<>(myRemotes.values());
    for (RemoteExternalSystemService<S> service : services) {
      service.setSettings(settings);
    }
  }

  @Override
  public void applyProgressManager(@NotNull RemoteExternalSystemProgressNotificationManager progressManager) throws RemoteException {
    ExternalSystemTaskNotificationListener listener = new SwallowingNotificationListener(progressManager);
    myNotificationListener.set(listener);
    myProjectResolver.setNotificationListener(listener);
    myTaskManager.setNotificationListener(listener);
  }

  @Override
  public boolean cancelTask(@NotNull ExternalSystemTaskId id) throws RemoteException {
    if(id.getType() == ExternalSystemTaskType.RESOLVE_PROJECT) {
      return myProjectResolver.cancelTask(id);
    } else{
      return myTaskManager.cancelTask(id);
    }
  }

  private static class SwallowingNotificationListener implements ExternalSystemTaskNotificationListener {

    private final @NotNull RemoteExternalSystemProgressNotificationManager myManager;

    SwallowingNotificationListener(@NotNull RemoteExternalSystemProgressNotificationManager manager) {
      myManager = manager;
    }

    @Override
    public synchronized void onStart(@NotNull ExternalSystemTaskId id, String workingDir) {
    }

    @Override
    public synchronized void onStart(@NotNull ExternalSystemTaskId id) {
    }


    @Override
    public synchronized void onEnvironmentPrepared(@NotNull ExternalSystemTaskId id) {
      try {
        myManager.onEnvironmentPrepared(id);
      }
      catch (RemoteException e) {
        // Ignore
      }
    }

    @Override
    public synchronized void onStatusChange(@NotNull ExternalSystemTaskNotificationEvent event) {
      try {
        myManager.onStatusChange(event);
      }
      catch (RemoteException e) {
        // Ignore
      }
    }

    @Override
    public synchronized void onTaskOutput(@NotNull ExternalSystemTaskId id, @NotNull String text, boolean stdOut) {
      try {
        myManager.onTaskOutput(id, text, stdOut);
      }
      catch (RemoteException e) {
        // Ignore
      }
    }

    @Override
    public synchronized void onEnd(@NotNull ExternalSystemTaskId id) {
    }

    @Override
    public synchronized void onSuccess(@NotNull ExternalSystemTaskId id) {
    }

    @Override
    public synchronized void onFailure(@NotNull ExternalSystemTaskId id, @NotNull Exception ex) {
    }

    @Override
    public synchronized void beforeCancel(@NotNull ExternalSystemTaskId id) {
    }

    @Override
    public synchronized void onCancel(@NotNull ExternalSystemTaskId id) {
    }
  }
}
