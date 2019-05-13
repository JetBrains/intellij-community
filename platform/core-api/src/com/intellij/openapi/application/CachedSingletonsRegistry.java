/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.application;

import com.intellij.openapi.util.ClearableLazyValue;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class CachedSingletonsRegistry {
  private static final Object LOCK = new CachedSingletonsRegistry();
  private static final List<Class<?>> ourRegisteredClasses = new ArrayList<>();
  private static final List<ClearableLazyValue<?>> ourRegisteredLazyValues = new ArrayList<>();

  private CachedSingletonsRegistry() {}

  @Nullable
  public static <T> T markCachedField(@NotNull Class<T> klass) {
    synchronized (LOCK) {
      ourRegisteredClasses.add(klass);
    }
    return null;
  }

  @NotNull
  public static <T> ClearableLazyValue<T> markLazyValue(@NotNull ClearableLazyValue<T> lazyValue) {
    synchronized (LOCK) {
      ourRegisteredLazyValues.add(lazyValue);
    }
    return lazyValue;
  }

  public static void cleanupCachedFields() {
    synchronized (LOCK) {
      for (Class<?> aClass : ourRegisteredClasses) {
        try {
          cleanupClass(aClass);
        }
        catch (Exception e) {
          // Ignore cleanup failed. In some cases we cannot find ourInstance field if idea.jar is scrambled and names of the private fields changed
        }
      }
      for (ClearableLazyValue<?> value : ourRegisteredLazyValues) {
        value.drop();
      }
    }
  }

  private static void cleanupClass(Class<?> aClass) {
    ReflectionUtil.resetField(aClass, null, "ourInstance");
  }
}
