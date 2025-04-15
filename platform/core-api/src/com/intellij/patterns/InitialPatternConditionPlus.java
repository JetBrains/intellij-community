// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.patterns;

import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

public abstract class InitialPatternConditionPlus<T> extends InitialPatternCondition<T> {
  protected InitialPatternConditionPlus(Class<T> aAcceptedClass) {
    super(aAcceptedClass);
  }

  public abstract @Unmodifiable List<ElementPattern<?>> getPatterns();
}
