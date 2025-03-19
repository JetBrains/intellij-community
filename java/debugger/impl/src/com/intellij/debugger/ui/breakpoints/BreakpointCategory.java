// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.breakpoints;

import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 */
public final class BreakpointCategory {
  private static final Map<String, Key> ourMap = new HashMap<>();

  private BreakpointCategory() {
  }

  public static @NotNull <T extends Breakpoint> Key<T> lookup(String name) {
    Key<T> key = ourMap.get(name);
    if (key == null) {
      key = Key.create(name);
      ourMap.put(name, key);
    }
    return key;
  }
}
