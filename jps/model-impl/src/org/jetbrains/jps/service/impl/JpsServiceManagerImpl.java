// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.service.impl;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.jps.plugin.JpsPluginManager;
import org.jetbrains.jps.service.JpsServiceManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class JpsServiceManagerImpl extends JpsServiceManager {
  private final ConcurrentMap<Class<?>, Object> myServices = new ConcurrentHashMap<>(16, 0.75f, 1);
  private final Map<Class<?>, List<?>> myExtensions = new HashMap<>();
  private final AtomicInteger myModificationStamp = new AtomicInteger(0);

  @Override
  public <T> T getService(Class<T> serviceClass) {
    //noinspection unchecked
    T service = (T)myServices.get(serviceClass);
    if (service == null) {
      // confine costly service initialization to single thread for defined startup profile
      synchronized (myServices) {
        //noinspection unchecked
        service = (T)myServices.get(serviceClass);
        if (service == null) {
          final Iterator<T> iterator = ServiceLoader.load(serviceClass, serviceClass.getClassLoader()).iterator();
          if (!iterator.hasNext()) {
            throw new ServiceConfigurationError("Implementation for " + serviceClass + " not found");
          }
          final T loadedService = iterator.next();
          if (iterator.hasNext()) {
            throw new ServiceConfigurationError("More than one implementation for " + serviceClass + " found: " + loadedService.getClass() +
              " and " + iterator.next().getClass());
          }
          //noinspection unchecked
          service = (T)myServices.putIfAbsent(serviceClass, loadedService);
          if (service == null) {
            service = loadedService;
          }
        }
      }
    }
    return service;
  }

  @Override
  public <T> Iterable<T> getExtensions(Class<T> extensionClass) {
    // confine costly service initialization to single thread for defined startup profile
    synchronized (myExtensions) {
      List<?> cached = cleanupExtensionCache()? null : myExtensions.get(extensionClass);
      if (cached == null) {
        myExtensions.put(extensionClass, cached = new ArrayList<>(JpsPluginManager.getInstance().loadExtensions(extensionClass)));
      }
      //noinspection unchecked
      return (List<T>)cached;
    }
  }

  @ApiStatus.Internal
  public boolean cleanupExtensionCache() {
    synchronized (myExtensions) {
      JpsPluginManager manager = JpsPluginManager.getInstance();
      int stamp = manager.getModificationStamp();
      if (myModificationStamp.getAndSet(stamp) != stamp) {
        myExtensions.clear();
        return true;
      }
      return false;
    }
  }
}
