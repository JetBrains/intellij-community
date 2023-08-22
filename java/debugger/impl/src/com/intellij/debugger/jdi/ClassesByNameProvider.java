// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.jdi;

import com.intellij.util.containers.MultiMap;
import com.jetbrains.jdi.JNITypeParser;
import com.sun.jdi.ObjectCollectedException;
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
      for (ReferenceType t : classes) {
        try {
          myCache.putValue(t.signature(), t);
        }
        catch (ObjectCollectedException ignored) { // skip already collected
        }
      }
    }

    @Override
    public List<ReferenceType> get(@NotNull String s) {
      return (List<ReferenceType>)myCache.get(JNITypeParser.typeNameToSignature(s));
    }
  }
}
