/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.psi.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public interface CachedValueProvider<T> {
  @Nullable
  Result<T> compute();

  class Result<T> {
    private static final Logger LOG = Logger.getInstance("#com.intellij.psi.util.CachedValueProvider.Result");
    private final T myValue;
    private final Object[] myDependencyItems;

    public Result(@Nullable T value, @NotNull Object... dependencyItems) {
      myValue = value;
      myDependencyItems = dependencyItems;

      if (dependencyItems.length == 0) {
        LOG.error("No dependencies provided which causes CachedValue to be never recalculated again. " +
                  "If this is intentional, please use ModificationTracker.NEVER_CHANGED");
      }
      for (int i = 0; i < dependencyItems.length; i++) {
        if (dependencyItems[i] == null) {
          LOG.error("Null dependencies are not allowed, index=" + i);
        }
      }
    }

    public T getValue() {
      return myValue;
    }

    @NotNull
    public Object[] getDependencyItems() {
      return myDependencyItems;
    }

    public static <T> Result<T> createSingleDependency(@Nullable T value, @NotNull Object dependency) {
      return create(value, dependency);
    }

    public static <T> Result<T> create(@Nullable T value, @NotNull Object... dependencies) {
      return new Result<T>(value, dependencies);
    }

    public static <T> Result<T> create(@Nullable T value, @NotNull Collection<?> dependencies) {
      return new Result<T>(value, ArrayUtil.toObjectArray(dependencies));
    }

  }
}
