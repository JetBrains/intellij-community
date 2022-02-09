// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.javac;

import com.intellij.util.BooleanFunction;
import com.intellij.util.Function;

import javax.tools.*;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 */
public final class FileObjectKindFilter<T> {
  private final Function<? super T, String> myToNameConverter;
  private final Map<JavaFileObject.Kind, BooleanFunction<T>> myFilterMap;

  public FileObjectKindFilter(Function<? super T, String> toNameConverter) {
    myToNameConverter = toNameConverter;
    final Map<JavaFileObject.Kind, BooleanFunction<T>> filterMap = new EnumMap<JavaFileObject.Kind, BooleanFunction<T>>(JavaFileObject.Kind.class);
    for (final JavaFileObject.Kind kind : JavaFileObject.Kind.values()) {
      if (kind == JavaFileObject.Kind.OTHER) {
        filterMap.put(kind, new BooleanFunction<T>() {
          @Override
          public boolean fun(T data) {
            return JpsFileObject.findKind(myToNameConverter.fun(data)) == JavaFileObject.Kind.OTHER;
          }
        });
      }
      else {
        filterMap.put(kind, new BooleanFunction<T>() {
          @Override
          public boolean fun(T data) {
            final String name = myToNameConverter.fun(data);
            return name.regionMatches(true, name.length() - kind.extension.length(), kind.extension, 0, kind.extension.length());
          }
        });
      }
    }
    myFilterMap = Collections.unmodifiableMap(filterMap);
  }

  public BooleanFunction<T> getFor(final Set<JavaFileObject.Kind> kinds) {
    // optimization for a single-element collection
    final Iterator<JavaFileObject.Kind> it = kinds.iterator();
    if (it.hasNext()) {
      final JavaFileObject.Kind kind = it.next();
      if (!it.hasNext()) {
        return myFilterMap.get(kind);
      }
    }
    // OR-filter, quite rare case
    return new BooleanFunction<T>() {
      @Override
      public boolean fun(T data) {
        for (JavaFileObject.Kind kind : kinds) {
          if (myFilterMap.get(kind).fun(data)) {
            return true;
          }
        }
        return false;
      }
    };
  }

}
