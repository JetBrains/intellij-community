/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.openapi.util;

import com.intellij.util.ReflectionCache;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

public class ClassExtension<T> extends KeyedExtensionCollector<T, Class> {
  public ClassExtension(@NonNls final String epName) {
    super(epName);
  }

  @Override
  protected String keyToString(final Class key) {
    return key.getName();
  }

  @Override
  protected List<T> buildExtensions(final String key, final Class classKey) {
    final Set<String> allSupers = new THashSet<String>();
    collectSupers(classKey, allSupers);
    return buildExtensions(allSupers);
  }

  private static void collectSupers(Class classKey, Set<String> allSupers) {
    allSupers.add(classKey.getName());
    final Class[] interfaces = ReflectionCache.getInterfaces(classKey);
    for (final Class anInterface : interfaces) {
      collectSupers(anInterface, allSupers);
    }

    final Class superClass = ReflectionCache.getSuperClass(classKey);
    if (superClass != null) {
      collectSupers(superClass, allSupers);
    }
  }

  @Nullable
  public T forClass(Class t) {
    final List<T> ts = forKey(t);
    return ts.isEmpty() ? null : ts.get(0);
  }
}