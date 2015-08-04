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

package com.intellij.openapi.module;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class ModuleServiceManager {
  private static final Logger LOG = Logger.getInstance(ModuleServiceManager.class);

  private ModuleServiceManager() {
  }

  @Nullable
  public static <T> T getService(@NotNull Module module, @NotNull Class<T> serviceClass) {
    //noinspection unchecked
    T instance = (T)module.getPicoContainer().getComponentInstance(serviceClass.getName());
    if (instance == null) {
      instance = module.getComponent(serviceClass);
      if (instance != null) {
        Application app = ApplicationManager.getApplication();
        String message = serviceClass.getName() + " requested as a service, but it is a component - convert it to a service or change call to module.getComponent()";
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
}