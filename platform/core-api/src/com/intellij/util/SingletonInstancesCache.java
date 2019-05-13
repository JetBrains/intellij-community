/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.util;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentMap;

/**
 * @author Sergey Evdokimov
 */
public class SingletonInstancesCache {

  private static final ConcurrentMap<String, Object> CACHE = ContainerUtil.newConcurrentMap();

  private SingletonInstancesCache() {
  }

  @SuppressWarnings("unchecked")
  public static <T> T getInstance(@NotNull String className, ClassLoader classLoader) {
    Object res = CACHE.get(className);
    if (res == null) {
      try {
        res = classLoader.loadClass(className).newInstance();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }

      Object oldValue = CACHE.putIfAbsent(className, res);
      if (oldValue != null) {
        res = oldValue;
      }
    }

    return (T)res;
  }
}
