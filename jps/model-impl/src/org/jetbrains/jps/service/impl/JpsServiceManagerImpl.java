package org.jetbrains.jps.service.impl;

import org.jetbrains.jps.service.JpsServiceManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author nik
 */
public class JpsServiceManagerImpl extends JpsServiceManager {
  private final ConcurrentHashMap<Class, Object> myServices = new ConcurrentHashMap<Class, Object>();
  private final ConcurrentHashMap<Class, List<?>> myExtensions = new ConcurrentHashMap<Class, List<?>>();

  @Override
  public <T> T getService(Class<T> serviceClass) {
    //noinspection unchecked
    T service = (T)myServices.get(serviceClass);
    if (service == null) {
      final Iterator<T> iterator = ServiceLoader.load(serviceClass, serviceClass.getClassLoader()).iterator();
      if (!iterator.hasNext()) {
        throw new ServiceConfigurationError("Implementation for " + serviceClass + " not found");
      }
      service = iterator.next();
      if (iterator.hasNext()) {
        throw new ServiceConfigurationError(
          "More than one implementation for " + serviceClass + " found: " + service.getClass() + " and " + iterator.next().getClass());
      }
      myServices.putIfAbsent(serviceClass, service);
      //noinspection unchecked
      service = (T)myServices.get(serviceClass);
    }
    return service;
  }

  @Override
  public <T> Iterable<T> getExtensions(Class<T> extensionClass) {
    List<?> cached = myExtensions.get(extensionClass);
    if (cached == null) {
      final ServiceLoader<T> loader = ServiceLoader.load(extensionClass, extensionClass.getClassLoader());
      List<T> extensions = new ArrayList<T>();
      for (T t : loader) {
        extensions.add(t);
      }
      myExtensions.putIfAbsent(extensionClass, extensions);
      cached = myExtensions.get(extensionClass);
    }
    //noinspection unchecked
    return (List<T>)cached;
  }
}
