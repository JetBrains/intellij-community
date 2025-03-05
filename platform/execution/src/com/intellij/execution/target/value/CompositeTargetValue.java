// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.target.value;

import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.util.Collection;

class CompositeTargetValue<S, T> implements TargetValue<T> {
  private final @NotNull Collection<? extends TargetValue<S>> myValues;
  private final @NotNull Function<? super Collection<S>, ? extends T> myMapper;

  CompositeTargetValue(@NotNull Collection<? extends TargetValue<S>> values, @NotNull Function<? super Collection<S>, ? extends T> mapper) {
    myValues = values;
    myMapper = mapper;
  }

  @Override
  public Promise<T> getLocalValue() {
    return Promises.collectResults(ContainerUtil.map(myValues, TargetValue::getLocalValue)).then(myMapper);
  }

  @Override
  public Promise<T> getTargetValue() {
    return Promises.collectResults(ContainerUtil.map(myValues, TargetValue::getTargetValue)).then(myMapper);
  }
}
