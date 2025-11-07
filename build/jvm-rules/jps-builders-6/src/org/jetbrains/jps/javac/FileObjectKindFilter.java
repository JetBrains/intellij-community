// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.javac;

import javax.tools.JavaFileObject;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

final class FileObjectKindFilter<T> {
  private final Function<? super T, String> myToNameConverter;
  private final Map<JavaFileObject.Kind, Predicate<T>> myFilterMap;

  FileObjectKindFilter(Function<? super T, String> toNameConverter) {
    myToNameConverter = toNameConverter;
    final Map<JavaFileObject.Kind, Predicate<T>> filterMap = new EnumMap<>(JavaFileObject.Kind.class);
    for (final JavaFileObject.Kind kind : JavaFileObject.Kind.values()) {
      if (kind == JavaFileObject.Kind.OTHER) {
        filterMap.put(kind, data -> JpsFileObject.findKind(myToNameConverter.apply(data)) == JavaFileObject.Kind.OTHER);
      }
      else {
        filterMap.put(kind, data -> {
          final String name = myToNameConverter.apply(data);
          return name.regionMatches(true, name.length() - kind.extension.length(), kind.extension, 0, kind.extension.length());
        });
      }
    }
    myFilterMap = Collections.unmodifiableMap(filterMap);
  }

  public Predicate<T> getFor(final Set<JavaFileObject.Kind> kinds) {
    // optimization for a single-element collection
    final Iterator<JavaFileObject.Kind> it = kinds.iterator();
    if (it.hasNext()) {
      final JavaFileObject.Kind kind = it.next();
      if (!it.hasNext()) {
        return myFilterMap.get(kind);
      }
    }
    // OR-filter, quite rare case
    return data -> {
      for (JavaFileObject.Kind kind : kinds) {
        if (myFilterMap.get(kind).test(data)) {
          return true;
        }
      }
      return false;
    };
  }

}
