/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.util.KeyedLazyInstance;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public class ClassExtension<T> extends KeyedExtensionCollector<T, Class> {
  public ClassExtension(@NonNls final String epName) {
    super(epName);
  }

  @NotNull
  @Override
  protected String keyToString(@NotNull final Class key) {
    return key.getName();
  }

  @NotNull
  @Override
  protected List<T> buildExtensions(@NotNull final String key, @NotNull final Class classKey) {
    final Set<String> allSupers = new LinkedHashSet<>();
    collectSupers(classKey, allSupers);
    return buildExtensionsWithInheritance(allSupers);
  }

  private List<T> buildExtensionsWithInheritance(Set<String> supers) {
    synchronized (lock) {
      List<T> result = null;
      for (String aSuper : supers) {
        result = buildExtensionsFromExplicitRegistration(result, key -> aSuper.equals(key));
      }
      for (String aSuper : supers) {
        result = buildExtensionsFromExtensionPoint(result, bean -> aSuper.equals(bean.getKey()));
      }
      return result == null ? Collections.emptyList() : result;
    }
  }

  private static void collectSupers(@NotNull Class classKey, @NotNull Set<? super String> allSupers) {
    allSupers.add(classKey.getName());
    final Class[] interfaces = classKey.getInterfaces();
    for (final Class anInterface : interfaces) {
      collectSupers(anInterface, allSupers);
    }

    final Class superClass = classKey.getSuperclass();
    if (superClass != null) {
      collectSupers(superClass, allSupers);
    }
  }

  @Nullable
  public T forClass(@NotNull Class t) {
    final List<T> ts = forKey(t);
    return ts.isEmpty() ? null : ts.get(0);
  }
}