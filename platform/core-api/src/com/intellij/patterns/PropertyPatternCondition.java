// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.patterns;

import com.intellij.util.PairProcessor;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class PropertyPatternCondition<T,P> extends PatternConditionPlus<T, P>{

  public PropertyPatternCondition(@NonNls String methodName, final ElementPattern propertyPattern) {
    super(methodName, propertyPattern);
  }

  @Override
  public boolean processValues(T t, ProcessingContext context, PairProcessor<? super P, ? super ProcessingContext> processor) {
    return processor.process(getPropertyValue(t), context);
  }

  public abstract @Nullable P getPropertyValue(@NotNull Object o);
}
