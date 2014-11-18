/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.jps.service.impl;

import org.jetbrains.jps.plugin.JpsPluginManager;
import org.jetbrains.jps.service.JpsServiceManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author nik
 */
public class JpsServiceManagerImpl extends JpsServiceManager {
  private final ConcurrentMap<Class, Object> myServices = new ConcurrentHashMap<Class, Object>(16, 0.75f, 1);
  private final ConcurrentMap<Class, List<?>> myExtensions = new ConcurrentHashMap<Class, List<?>>(16, 0.75f, 1);

  @Override
  public <T> T getService(Class<T> serviceClass) {
    //noinspection unchecked
    T service = (T)myServices.get(serviceClass);
    if (service == null) {
      final Iterator<T> iterator = ServiceLoader.load(serviceClass, serviceClass.getClassLoader()).iterator();
      if (!iterator.hasNext()) {
        throw new ServiceConfigurationError("Implementation for " + serviceClass + " not found");
      }
      final T loadedService = iterator.next();
      if (iterator.hasNext()) {
        throw new ServiceConfigurationError(
          "More than one implementation for " + serviceClass + " found: " + loadedService.getClass() + " and " + iterator.next().getClass());
      }
      //noinspection unchecked
      service = (T)myServices.putIfAbsent(serviceClass, loadedService);
      if (service == null) {
        service = loadedService;
      }
    }
    return service;
  }

  @Override
  public <T> Iterable<T> getExtensions(Class<T> extensionClass) {
    List<?> cached = myExtensions.get(extensionClass);
    if (cached == null) {
      final List<T> extensions = new ArrayList<T>(JpsPluginManager.getInstance().loadExtensions(extensionClass));
      cached = myExtensions.putIfAbsent(extensionClass, extensions);
      if (cached == null) {
        cached = extensions;
      }
    }
    //noinspection unchecked
    return (List<T>)cached;
  }
}
