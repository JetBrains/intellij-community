/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.engine.DebugProcess;
import com.intellij.openapi.util.Key;
import com.intellij.util.containers.WeakHashMap;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author Denis Zhdanov
 * @since 7/18/11 2:43 PM
 */
public class DebuggerRendererUtil {

  /**
   * Holds mappings between particular values and their custom renderers.
   */
  private static final Key<Map<Value, Renderer>> RENDERERS_MAP_KEY = new Key<Map<Value, Renderer>>("ValueRendererMap");
  
  private DebuggerRendererUtil() {
  }

  /**
   * Asks for custom renderer registered for the given value at the given debug process (if any).
   * 
   * @param process     target debug process
   * @param value       target value which custom renderer is being retrieved
   * @param clazz       class that specifies IS-A constraint for the desired renderer
   * @return            <code>'value - custom renderer'</code> registered for the given value at the given debug process (if any);
   *                    <code>null</code> otherwise
   */
  @SuppressWarnings("unchecked")
  @Nullable
  public static <T extends Renderer> T getCustomRenderer(@Nullable final DebugProcess process, @NotNull Value value,
                                                         @NotNull Class<T> clazz)
  {
    if (process == null) {
      return null;
    }
    Map<Value, Renderer> map = process.getUserData(RENDERERS_MAP_KEY);
    if (map == null) {
      process.putUserData(RENDERERS_MAP_KEY, map = new WeakHashMap<Value, Renderer>());
    }
    final Renderer renderer = map.get(value);
    if (clazz.isInstance(renderer)) {
      return (T)renderer;
    }
    else {
      return null;
    } 
  }

  /**
   * Tries to store given custom renderer for the given value within the given debug process.
   * 
   * @param value     target value
   * @param process   target debug process
   * @param renderer  target custom renderer to store
   * @return          <code>true</code> if the mapping is successfully stored; <code>false</code> otherwise
   */
  public static boolean setCustomRenderer(@NotNull Value value, @Nullable DebugProcess process, @NotNull Renderer renderer) {
    if (process == null) {
      return false;
    }

    Map<Value, Renderer> map = process.getUserData(RENDERERS_MAP_KEY);
    if (map == null) {
      process.putUserData(RENDERERS_MAP_KEY, map = new WeakHashMap<Value, Renderer>());
    }
    map.put(value, renderer);
    return true;
  }
}
