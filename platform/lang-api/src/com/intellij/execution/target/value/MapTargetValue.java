// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target.value;

import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;

class MapTargetValue<S, T> implements TargetValue<T> {
  @NotNull private final TargetValue<? extends S> myOriginalValue;
  @NotNull private final Function<? super S, ? extends T> myMapper;

  MapTargetValue(@NotNull TargetValue<? extends S> originalValue, @NotNull Function<? super S, ? extends T> mapper) {
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
