// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ServiceLoader;

/**
 * @author Eugene Zhuravlev
 */
public final class ExtensionsSupport<T> {
  private final Class<T> myExtensionClass;
  private volatile List<T> myCached;

  public ExtensionsSupport(Class<T> extensionClass) {
    myExtensionClass = extensionClass;
  }

  @NotNull
  public Collection<T> getExtensions() {
    final Collection<T> cached = myCached;
    if (cached != null) {
      return cached;
    }
    final ServiceLoader<T> loader = ServiceLoader.load(myExtensionClass, myExtensionClass.getClassLoader());
    final List<T> extensions = new ArrayList<>();
    for (T extension : loader) {
      extensions.add(extension);
    }
    myCached = extensions;
    return extensions;
  }
}
