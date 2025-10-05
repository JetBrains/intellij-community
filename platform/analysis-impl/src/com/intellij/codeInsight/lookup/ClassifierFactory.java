// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public abstract class ClassifierFactory<T> {
  private final String myId;

  protected ClassifierFactory(@NonNls String id) {
    myId = id;
  }

  public String getId() {
    return myId;
  }

  public abstract @NotNull Classifier<T> createClassifier(@NotNull Classifier<T> next);

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ClassifierFactory that)) return false;

    if (!myId.equals(that.myId)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myId.hashCode();
  }
}
