/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.ui.breakpoints;

import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 18, 2007
 */
public final class BreakpointCategory {
  private static final Map<String, Key> ourMap = new HashMap<String, Key>();

  private BreakpointCategory() {
  }

  @NotNull
  public static <T extends Breakpoint> Key<T> lookup(String name) {
    Key<T> key = ourMap.get(name);
    if (key == null) {
      key = Key.create(name);
      ourMap.put(name, key);
    }
    return key;
  }
}
