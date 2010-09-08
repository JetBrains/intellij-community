package org.jetbrains.jps.idea;

import java.util.Iterator;

/**
 * This class should be used instead of {@link java.util.ServiceLoader} because the standard ServiceLoader
 * is not available in JDK 1.5
 *
 * @author nik
 */
public class ServiceLoader<S> implements Iterable<S> {
  private Class<S> serviceClass;

  private ServiceLoader(Class<S> serviceClass) {
    this.serviceClass = serviceClass;
  }

  public static <S> ServiceLoader<S> load(Class<S> serviceClass) {
    return new ServiceLoader<S>(serviceClass);
  }

  public Iterator<S> iterator() {
    return sun.misc.Service.providers(serviceClass);
  }
}
