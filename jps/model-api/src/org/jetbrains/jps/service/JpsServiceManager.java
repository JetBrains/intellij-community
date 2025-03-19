// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.service;

import org.jetbrains.annotations.ApiStatus;

import java.lang.reflect.InvocationTargetException;
import java.util.ServiceLoader;

public abstract class JpsServiceManager {
  public static JpsServiceManager getInstance() {
    return InstanceHolder.INSTANCE;
  }

  @ApiStatus.Internal
  protected JpsServiceManager() {
  }

  public abstract <T> T getService(Class<T> serviceClass);

  public abstract <T> Iterable<T> getExtensions(Class<T> extensionClass);

  private static final class InstanceHolder {
    private static final JpsServiceManager INSTANCE;

    static {
      String implClass = System.getProperties().getProperty("jps.service.manager.impl");
      if (implClass == null || implClass.isEmpty()) {
        INSTANCE = ServiceLoader.load(JpsServiceManager.class, JpsServiceManager.class.getClassLoader()).iterator().next();
      }
      else {
        try {
          Class<?> aClass = JpsServiceManager.class.getClassLoader().loadClass(implClass);
          INSTANCE = (JpsServiceManager)aClass.getDeclaredConstructor().newInstance();
        }
        catch (ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException |
               InvocationTargetException e) {
          throw new RuntimeException(e);
        }
      }
    }
  }
}
