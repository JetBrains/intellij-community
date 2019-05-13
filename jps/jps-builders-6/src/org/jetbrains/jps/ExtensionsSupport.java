/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ServiceLoader;

/**
 * @author Eugene Zhuravlev
 */
public class ExtensionsSupport<T> {

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
    final List<T> extensions = new ArrayList<T>();
    for (T extension : loader) {
      extensions.add(extension);
    }
    myCached = extensions;
    return extensions;
  }

}
