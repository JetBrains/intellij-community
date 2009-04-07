/*
 * @author max
 */
package com.intellij.openapi.application;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class CachedSingletonsRegistry {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.application.CachedSingletonsRegistry");

  private static final Object LOCK = new CachedSingletonsRegistry();
  private static final List<Class<?>> ourRegisteredClasses = new ArrayList<Class<?>>();

  private CachedSingletonsRegistry() {}

  @Nullable
  public static <T> T markCachedField(Class<T> klass) {
    synchronized (LOCK) {
      ourRegisteredClasses.add(klass);
    }
    return null;
  }

  public static void cleanupCachedFields() {
    synchronized (LOCK) {
      for (Class<?> aClass : ourRegisteredClasses) {
        try {
          cleanupClass(aClass);
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
    }
  }

  private static void cleanupClass(Class<?> aClass) throws Exception {
    Field field = aClass.getDeclaredField("ourInstance");
    field.setAccessible(true);
    field.set(null, null);
  }
}
