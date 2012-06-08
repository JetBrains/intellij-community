package org.jetbrains.jps.service.impl;

import org.jetbrains.jps.service.JpsServiceManager;

import java.util.Iterator;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author nik
 */
public class JpsServiceManagerImpl extends JpsServiceManager {
  private final ConcurrentHashMap<Class, Object> myServices = new ConcurrentHashMap<Class, Object>();

  @Override
  public <T> T getService(Class<T> serviceClass) {
    //noinspection unchecked
    T service = (T)myServices.get(serviceClass);
    if (service == null) {
      final Iterator<T> iterator = ServiceLoader.load(serviceClass).iterator();
      if (!iterator.hasNext()) {
        throw new ServiceConfigurationError("Implementation for " + serviceClass + " not found");
      }
      service = iterator.next();
      if (iterator.hasNext()) {
        throw new ServiceConfigurationError("More than one implementation for " + serviceClass + " found: " + service.getClass() + " and " + iterator.next().getClass());
      }
      myServices.putIfAbsent(serviceClass, service);
    }
    return service;
  }
}
