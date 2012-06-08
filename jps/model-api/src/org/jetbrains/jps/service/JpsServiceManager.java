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

  private static class InstanceHolder {
    private static final JpsServiceManager INSTANCE;
    static {
      INSTANCE = ServiceLoader.load(JpsServiceManager.class).iterator().next();
    }
  }
}
