package org.jetbrains.jps.idea;

import java.util.Iterator;

/**
 * This class should be used instead of {@link java.util.ServiceLoader} because the standard ServiceLoader
 * is not available in JDK 1.5
 *
 * @author nik
 */
public class OwnServiceLoader<S> implements Iterable<S> {
  private Class<S> serviceClass;

  private OwnServiceLoader(Class<S> serviceClass) {
    this.serviceClass = serviceClass;
  }

  public static <S> OwnServiceLoader<S> load(Class<S> serviceClass) {
    return new OwnServiceLoader<S>(serviceClass);
  }

  public Iterator<S> iterator() {
    return sun.misc.Service.providers(serviceClass);
  }
}
