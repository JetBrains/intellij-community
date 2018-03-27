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
