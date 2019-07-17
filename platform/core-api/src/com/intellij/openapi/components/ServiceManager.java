// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components;

import com.intellij.diagnostic.LoadingPhase;
import com.intellij.diagnostic.ParallelActivity;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NotNullLazyKey;
import com.intellij.openapi.util.Ref;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.storage.HeavyProcessLatch;
import com.intellij.util.pico.DefaultPicoContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.PicoContainer;

import java.lang.reflect.Modifier;
import java.util.concurrent.ConcurrentMap;

/**
 * For old-style components, the contract specifies a lifecycle: the component gets created and notified during the project opening process.
 * For services, there's no such contract, so we don't even load the class implementing the service until someone requests it.
 */
public final class ServiceManager {
  private static final Logger LOG = Logger.getInstance(ServiceManager.class);

  private static final ConcurrentMap<Class<?>, Object> ourAppServices = ContainerUtil.newConcurrentMap();

  private ServiceManager() { }

  public static <T> T getService(@NotNull Class<T> serviceClass) {
    if (isLightService(serviceClass)) {
      return getOrCreateLightService(serviceClass);
    }
    else {
      return doGetService(ApplicationManager.getApplication(), serviceClass, true);
    }
  }

  public static <T> T getService(@NotNull Project project, @NotNull Class<T> serviceClass) {
    return doGetService(project, serviceClass, true);
  }

  @Nullable
  public static <T> T getServiceIfCreated(@NotNull Project project, @NotNull Class<T> serviceClass) {
    return doGetService(project, serviceClass, false);
  }

  @Nullable
  public static <T> T getServiceIfCreated(@NotNull Class<T> serviceClass) {
    if (isLightService(serviceClass)) {
      //noinspection unchecked
      return (T)ourAppServices.get(serviceClass);
    }
    return doGetService(ApplicationManager.getApplication(), serviceClass, false);
  }

  @Nullable
  private static <T> T doGetService(@NotNull ComponentManager componentManager, @NotNull Class<T> serviceClass, boolean isCreate) {
    String componentKey = serviceClass.getName();

    PicoContainer picoContainer = componentManager.getPicoContainer();
    if (!isCreate && picoContainer instanceof DefaultPicoContainer) {
      return ((DefaultPicoContainer)picoContainer).getComponentInstanceIfInstantiated(componentKey);
    }

    @SuppressWarnings("unchecked") T instance = (T)picoContainer.getComponentInstance(componentKey);
    if (instance == null) {
      ProgressManager.checkCanceled();
      instance = assertServiceNotRegisteredAsComponent(componentManager, serviceClass, componentKey);
    }
    return instance;
  }

  private static <T> T assertServiceNotRegisteredAsComponent(@NotNull ComponentManager componentManager,
                                                             @NotNull Class<T> serviceClass,
                                                             @NotNull String componentKey) {
    T instance = componentManager.getComponent(serviceClass);
    if (instance != null) {
      Application app = ApplicationManager.getApplication();
      String message = componentKey + " requested as a service, but it is a component - convert it to a service or change call to " +
                       (componentManager == app ? "ApplicationManager.getApplication().getComponent()" : "project.getComponent()");
      if (app.isUnitTestMode()) {
        LOG.error(message);
      }
      else {
        LOG.warn(message);
      }
    }
    return instance;
  }

  /**
   * Creates lazy caching key to store project-level service instance from {@link #getService(Project, Class)}.
   *
   * @param serviceClass Service class to create key for.
   * @param <T>          Service class type.
   * @return Key instance.
   */
  @NotNull
  public static <T> NotNullLazyKey<T, Project> createLazyKey(@NotNull final Class<? extends T> serviceClass) {
    return NotNullLazyKey.create("Service: " + serviceClass.getName(), project -> getService(project, serviceClass));
  }

  private static <T> boolean isLightService(@NotNull Class<T> serviceClass) {
    return Modifier.isFinal(serviceClass.getModifiers()) && serviceClass.isAnnotationPresent(Service.class);
  }

  @NotNull
  private static <T> T getOrCreateLightService(@NotNull Class<T> serviceClass) {
    @SuppressWarnings("unchecked")
    T instance = (T)ourAppServices.get(serviceClass);
    if (instance != null) {
      return instance;
    }

    LoadingPhase.assertAtLeast(LoadingPhase.COMPONENT_REGISTERED);

    //noinspection SynchronizeOnThis
    synchronized (serviceClass) {
      //noinspection unchecked
      instance = (T)ourAppServices.get(serviceClass);
      if (instance != null) {
        return instance;
      }

      ComponentManager componentManager = ApplicationManager.getApplication();
      try (AccessToken ignore = HeavyProcessLatch.INSTANCE.processStarted("Creating service '" + serviceClass.getName() + "'")) {
        if (ProgressIndicatorProvider.getGlobalProgressIndicator() == null) {
          instance = createLightService(serviceClass, componentManager);
        }
        else {
          Ref<T> ref = new Ref<>();
          //noinspection CodeBlock2Expr
          ProgressManager.getInstance().executeNonCancelableSection(() -> {
            ref.set(createLightService(serviceClass, componentManager));
          });
          instance = ref.get();
        }
      }

      Object prevValue = ourAppServices.put(serviceClass, instance);
      LOG.assertTrue(prevValue == null);
      return instance;
    }
  }

  @NotNull
  private static <T> T createLightService(@NotNull Class<T> serviceClass, @NotNull ComponentManager componentManager) {
    long startTime = StartUpMeasurer.getCurrentTime();
    T instance = ReflectionUtil.newInstance(serviceClass, false);
    if (instance instanceof Disposable) {
      Disposer.register(componentManager, (Disposable)instance);
    }
    componentManager.initializeComponent(instance, null);
    ParallelActivity.SERVICE.record(startTime, instance.getClass(), StartUpMeasurer.Level.APPLICATION);
    return instance;
  }
}
