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
package com.intellij.openapi.util;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;

/**
 * @author peter
 */
@SuppressWarnings({"UtilityClassWithoutPrivateConstructor"})
public class RecursionManager {
  private static final ThreadLocal<Integer> ourRecursionsMet = new ThreadLocal<Integer>();
  private static final ThreadLocal<LinkedHashMap<Object, Set<Object>>> ourProgress = new ThreadLocal<LinkedHashMap<Object, Set<Object>>>();
  private static final ThreadLocal<Set<Object>> ourNoCache = new ThreadLocal<Set<Object>>();

  public static RecursionGuard createGuard(final String id) {
    return new RecursionGuard() {
      @Override
      public <T> T doPreventingRecursion(Object key, Computable<T> computation) {
        Object realKey = Pair.create(id, key);
        LinkedHashMap<Object, Set<Object>> progressMap = ourProgress.get();
        if (progressMap == null) {
          ourProgress.set(progressMap = new LinkedHashMap<Object, Set<Object>>());
        }
        else if (progressMap.containsKey(realKey)) {
          disableCachingForStackLoop(realKey, progressMap);

          ourRecursionsMet.set((ourRecursionsMet.get() != null ? ourRecursionsMet.get() : 0) + 1);

          return null;
        }

        progressMap.put(realKey, null);

        try {
          return computation.compute();
        }
        finally {
          Set<Object> deps = progressMap.remove(realKey);
          Set<Object> noCache = ourNoCache.get();
          if (noCache != null && deps != null) {
            noCache.removeAll(deps);
          }
        }
      }

      @Override
      public StackStamp markStack() {
        final Integer stamp = ourRecursionsMet.get();
        return new StackStamp() {
          @Override
          public boolean mayCacheNow() {
            return Comparing.equal(stamp, ourRecursionsMet.get());
          }
        };
      }
    };
  }

  private static void disableCachingForStackLoop(Object realKey, LinkedHashMap<Object, Set<Object>> progressMap) {
    Set<Object> noCache = ourNoCache.get();
    if (noCache == null) {
      ourNoCache.set(noCache = new HashSet<Object>());
    }

    Set<Object> deps = progressMap.get(realKey);
    if (deps == null) {
      progressMap.put(realKey, deps = new HashSet<Object>());
    }

    boolean inCycle = false;
    for (Object o : progressMap.keySet()) {
      if (inCycle || o.equals(realKey)) {
        inCycle = true;
        deps.add(o);
        noCache.add(o);
      }
    }
  }
}
