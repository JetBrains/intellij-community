// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target.value;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.util.Collection;
import java.util.function.Function;

public interface TargetValue<T> {
  T getLocalValue();

  T getTargetValue();

  Promise<TargetValue<T>> promise();

  static <V> TargetValue<V> empty() {
    //noinspection unchecked
    return EMPTY_VALUE;
  }

  static <V> TargetValue<V> fixed(V value) {
    return new FixedTargetValue<>(value);
  }

  static <T, V> TargetValue<V> map(@NotNull TargetValue<? extends T> originalValue, @NotNull Function<T, V> mapper) {
    return new MapTargetValue<>(originalValue, mapper);
  }

  static <T, V> TargetValue<V> composite(@NotNull Collection<TargetValue<T>> values, @NotNull Function<Collection<T>, V> joiner) {
    return new CompositeTargetValue<>(values, joiner);
  }

  @SuppressWarnings("rawtypes")
  TargetValue EMPTY_VALUE = new TargetValue() {
    @Override
    public Object getLocalValue() {
      return null;
    }

    @Override
    public Object getTargetValue() {
      return null;
    }

    @Override
    public Promise<TargetValue> promise() {
      return Promises.resolvedPromise(this);
    }
  };
}
