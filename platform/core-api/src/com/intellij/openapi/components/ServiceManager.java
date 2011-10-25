/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyKey;
import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.NotNull;


/**
 * For old-style components, the contract specifies a lifecycle: the component gets created and notified during the project opening process.
 * For services, there's no such contract, so we don't even load the class implementing the service until someone requests it.
 */
@SuppressWarnings({"unchecked"})
public class ServiceManager {
  private ServiceManager() {
  }

  public static <T> T getService(@NotNull Class<T> serviceClass) {
    return (T)ApplicationManager.getApplication().getPicoContainer().getComponentInstance(serviceClass.getName());
  }

  public static <T> T getService(@NotNull Project project, @NotNull Class<T> serviceClass) {
    return (T)project.getPicoContainer().getComponentInstance(serviceClass.getName());
  }

  /**
   * Creates lazy caching key to store project-level service instance from {@link #getService(com.intellij.openapi.project.Project, Class)}.
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
