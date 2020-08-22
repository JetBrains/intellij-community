// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * For old-style components, the contract specifies a lifecycle: the component gets created and notified during the project opening process.
 * For services, there's no such contract, so we don't even load the class implementing the service until someone requests it.
 *
 * In a new code please use {@link ComponentManager#getService(Class)} or {@link ComponentManager#getServiceIfCreated(Class)}.
 */
public final class ServiceManager {
  private ServiceManager() { }

  public static <T> T getService(@NotNull Class<T> serviceClass) {
    return ApplicationManager.getApplication().getService(serviceClass);
  }

  public static <T> T getService(@NotNull Project project, @NotNull Class<T> serviceClass) {
    return project.getService(serviceClass);
  }

  /**
   * @deprecated Use {@link ComponentManager#getServiceIfCreated(Class)}.
   */
  @Deprecated
  public static @Nullable <T> T getServiceIfCreated(@NotNull Project project, @NotNull Class<T> serviceClass) {
    return project.getServiceIfCreated(serviceClass);
  }

  /**
   * @deprecated Use {@link ComponentManager#getServiceIfCreated(Class)}.
   */
  @Deprecated
  public static @Nullable <T> T getServiceIfCreated(@NotNull Class<T> serviceClass) {
    return ApplicationManager.getApplication().getServiceIfCreated(serviceClass);
  }

  /**
   * Creates lazy caching key to store project-level service instance from {@link Project#getService(Class)}.
   *
   * @param serviceClass Service class to create key for.
   * @param <T>          Service class type.
   * @return Key instance.
   * @deprecated Don't use this method; it has no benefit over normal ServiceManager.getService
   */
  @Deprecated
  public static @NotNull <T> NotNullLazyKey<T, Project> createLazyKey(final @NotNull Class<? extends T> serviceClass) {
    return NotNullLazyKey.create("Service: " + serviceClass.getName(), project -> project.getService(serviceClass));
  }
}
