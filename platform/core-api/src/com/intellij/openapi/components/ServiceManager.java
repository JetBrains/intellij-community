/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.components;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyKey;
import com.intellij.util.NotNullFunction;
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
    return doGetService(application, serviceClass);
  }

  public static <T> T getService(@NotNull Project project, @NotNull Class<T> serviceClass) {
    return doGetService(project, serviceClass);
  }

  @Nullable
  private static <T> T doGetService(ComponentManager componentManager, @NotNull Class<T> serviceClass) {
    PicoContainer picoContainer = componentManager.getPicoContainer();
    @SuppressWarnings("unchecked") T instance = (T)picoContainer.getComponentInstance(serviceClass.getName());
    if (instance == null) {
      instance = componentManager.getComponent(serviceClass);
      if (instance != null) {
        Application app = ApplicationManager.getApplication();
        String message = serviceClass.getName() + " requested as a service, but it is a component - convert it to a service or change call to " +
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
  public static <T> NotNullLazyKey<T, Project> createLazyKey(@NotNull final Class<T> serviceClass) {
    return NotNullLazyKey.create("Service: " + serviceClass.getName(), new NotNullFunction<Project, T>() {
      @Override
      @NotNull
      public T fun(Project project) {
        return getService(project, serviceClass);
      }
    });
  }
}
