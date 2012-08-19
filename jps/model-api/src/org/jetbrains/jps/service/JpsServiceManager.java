package org.jetbrains.jps.service;

import java.util.ServiceLoader;

/**
 * @author nik
 */
public abstract class JpsServiceManager {
  public static JpsServiceManager getInstance() {
    return InstanceHolder.INSTANCE;
  }

  public abstract <T> T getService(Class<T> serviceClass);

  public abstract <T> Iterable<T> getExtensions(Class<T> extensionClass);

  private static class InstanceHolder {
    private static final JpsServiceManager INSTANCE;

    static {
      INSTANCE = ServiceLoader.load(JpsServiceManager.class, JpsServiceManager.class.getClassLoader()).iterator().next();
    }
  }
}
