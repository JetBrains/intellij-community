// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.jdi;

import com.intellij.util.containers.MultiMap;
import com.jetbrains.jdi.JNITypeParser;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface ClassesByNameProvider {
  List<ReferenceType> get(@NotNull String s);

  static ClassesByNameProvider createCache(List<ReferenceType> allTypes) {
    return new Cache(allTypes);
  }

  final class Cache implements ClassesByNameProvider {
    private final MultiMap<String, ReferenceType> myCache;

    public Cache(List<ReferenceType> classes) {
      myCache = new MultiMap<>();
      classes.forEach(t -> myCache.putValue(t.signature(), t));
    }

    @Override
    public List<ReferenceType> get(@NotNull String s) {
      return (List<ReferenceType>)myCache.get(JNITypeParser.typeNameToSignature(s));
    }
  }
}
