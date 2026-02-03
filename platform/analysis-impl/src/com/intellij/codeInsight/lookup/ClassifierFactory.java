// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

@ApiStatus.NonExtendable
public abstract class ClassifierFactory<T> {
  private final @NotNull String myId;

  protected ClassifierFactory(@NotNull @NonNls String id) {
    myId = id;
  }

  public @NotNull @NonNls String getId() {
    return myId;
  }

  public abstract @NotNull Classifier<T> createClassifier(@NotNull Classifier<T> next);

  @Override
  public boolean equals(Object o) {
    return this == o || o instanceof ClassifierFactory that && myId.equals(that.myId);
  }

  @Override
  public int hashCode() {
    return myId.hashCode();
  }
}
