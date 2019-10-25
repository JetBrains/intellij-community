// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target.value;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;

import java.util.function.Function;

class MapTargetValue<S, T> implements TargetValue<T> {
  @NotNull private final TargetValue<? extends S> myOriginalValue;
  @NotNull private final Function<S, T> myMapper;

  MapTargetValue(@NotNull TargetValue<? extends S> originalValue, @NotNull Function<S, T> mapper) {
    myOriginalValue = originalValue;
    myMapper = mapper;
  }

  @Override
  public T getLocalValue() {
    return myMapper.apply(myOriginalValue.getLocalValue());
  }

  @Override
  public T getTargetValue() {
    return myMapper.apply(myOriginalValue.getTargetValue());
  }

  @Override
  public Promise<TargetValue<T>> promise() {
    return myOriginalValue.promise().then(__ -> this);
  }
}
