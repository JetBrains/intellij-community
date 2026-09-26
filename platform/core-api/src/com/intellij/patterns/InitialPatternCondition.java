// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.patterns;

import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class InitialPatternCondition<T> {
  private final Class<T> myAcceptedClass;

  protected InitialPatternCondition(@NotNull Class<T> aAcceptedClass) {
    myAcceptedClass = aAcceptedClass;
  }

  public @NotNull Class<T> getAcceptedClass() {
    return myAcceptedClass;
  }

  public boolean accepts(@Nullable Object o, final ProcessingContext context) {
    return myAcceptedClass.isInstance(o);
  }

  @Override
  public final @NonNls String toString() {
    StringBuilder builder = new StringBuilder();
    append(builder, "");
    return builder.toString();
  }

  public void append(@NonNls @NotNull StringBuilder builder, final String indent) {
    builder.append("instanceOf(").append(myAcceptedClass.getSimpleName()).append(")");
  }
}
