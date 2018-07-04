// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyKey;
import com.intellij.util.pico.DefaultPicoContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.PicoContainer;

/**
 * For old-style components, the contract specifies a lifecycle: the component gets created and notified during the project opening process.
 * For services, there's no such contract, so we don't even load the class implementing the service until someone requests it.
 */
public class ServiceManager {
  private static final Logger LOG = Logger.getInstance(ServiceManager.class);

  private ServiceManager() { }

  public static <T> T getService(@NotNull Class<T> serviceClass) {
    Application application = ApplicationManager.getApplication();
    return doGetService(application, serviceClass, true);
  }

  public static <T> T getService(@NotNull Project project, @NotNull Class<T> serviceClass) {
    return doGetService(project, serviceClass, true);
  }

  @Nullable
  public static <T> T getServiceIfCreated(@NotNull Project project, @NotNull Class<T> serviceClass) {
    return doGetService(project, serviceClass, false);
  }

  @Nullable
  private static <T> T doGetService(ComponentManager componentManager, @NotNull Class<T> serviceClass, boolean isCreate) {
    String componentKey = serviceClass.getName();

    PicoContainer picoContainer = componentManager.getPicoContainer();
    if (!isCreate && picoContainer instanceof DefaultPicoContainer) {
      return ((DefaultPicoContainer)picoContainer).getComponentInstanceIfInstantiated(componentKey);
    }

    @SuppressWarnings("unchecked") T instance = (T)picoContainer.getComponentInstance(componentKey);
    if (instance == null) {
      ProgressManager.checkCanceled();
      instance = componentManager.getComponent(serviceClass);
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
  public static <T> NotNullLazyKey<T, Project> createLazyKey(@NotNull final Class<T> serviceClass) {
    return NotNullLazyKey.create("Service: " + serviceClass.getName(), project -> getService(project, serviceClass));
  }
}
