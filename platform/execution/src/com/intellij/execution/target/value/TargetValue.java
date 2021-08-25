// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target.value;

import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.util.Collection;

public interface TargetValue<T> {
  Promise<T> getLocalValue();

  Promise<T> getTargetValue();

  static <V> TargetValue<V> empty() {
    //noinspection unchecked
    return EMPTY_VALUE;
  }

  static <V> TargetValue<V> fixed(V value) {
    return new FixedTargetValue<>(value);
  }

  static <T, V> TargetValue<V> map(@NotNull TargetValue<? extends T> originalValue, @NotNull Function<? super T, ? extends V> mapper) {
    return new MapTargetValue<>(originalValue, mapper);
  }

  static <T, V> TargetValue<V> composite(@NotNull Collection<TargetValue<T>> values, @NotNull Function<? super Collection<T>, ? extends V> joiner) {
    return new CompositeTargetValue<>(values, joiner);
  }

  static <V> TargetValue<V> create(@NotNull V localValue, @NotNull Promise<V> targetValue){
    return new PromiseBasedTargetValue<>(localValue, targetValue);
  }

  @SuppressWarnings("rawtypes")
  TargetValue EMPTY_VALUE = new TargetValue() {
    @Override
    public Promise<Object> getLocalValue() {
      return Promises.resolvedPromise();
    }

    @Override
    public Promise<Object> getTargetValue() {
      return Promises.resolvedPromise();
    }
  };
}
