// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.jdi;

import com.intellij.util.containers.MultiMap;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author egor
 */
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

    public List<ReferenceType> get(@NotNull String s) {
      String signature = VirtualMachineProxyImpl.JNITypeParserReflect.typeNameToSignature(s);
      if (signature != null) {
        return (List<ReferenceType>)myCache.get(signature);
      }
      return Collections.emptyList();
    }
  }
}
