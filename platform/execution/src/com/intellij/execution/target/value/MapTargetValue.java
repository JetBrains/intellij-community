// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.target.value;

import com.intellij.util.Function;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;

@ApiStatus.Internal
public class MapTargetValue<S, T> implements TargetValue<T> {
  private final @NotNull TargetValue<? extends S> myOriginalValue;
  private final @NotNull Function<? super S, ? extends T> myMapper;

  public MapTargetValue(@NotNull TargetValue<? extends S> originalValue, @NotNull Function<? super S, ? extends T> mapper) {
    myOriginalValue = originalValue;
    myMapper = mapper;
  }

  @Override
  public Promise<T> getLocalValue() {
    return myOriginalValue.getLocalValue().then(myMapper);
  }

  @Override
  public Promise<T> getTargetValue() {
    return myOriginalValue.getTargetValue().then(myMapper);
  }
}
