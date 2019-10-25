// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target.value;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.util.Collection;
import java.util.function.Function;

class CompositeTargetValue<S, T> implements TargetValue<T> {
  @NotNull private final Collection<TargetValue<S>> myValues;
  @NotNull private final Function<Collection<S>, T> myMapper;

  CompositeTargetValue(@NotNull Collection<TargetValue<S>> values, @NotNull Function<Collection<S>, T> mapper) {
    myValues = values;
    myMapper = mapper;
  }

  @Override
  public T getLocalValue() {
    return myMapper.apply(ContainerUtil.map(myValues, TargetValue::getLocalValue));
  }

  @Override
  public T getTargetValue() {
    return myMapper.apply(ContainerUtil.map(myValues, TargetValue::getTargetValue));
  }

  @Override
  public Promise<TargetValue<T>> promise() {
    return Promises.collectResults(ContainerUtil.map(myValues, TargetValue::promise)).then(__ -> this);
  }
}
