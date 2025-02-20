// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.bytecodeAnalysis;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

@ApiStatus.Internal
public final class Equations {
  final @NotNull List<DirectionResultPair> results;
  final boolean stable;

  Equations(@NotNull List<DirectionResultPair> results, boolean stable) {
    this.results = results;
    this.stable = stable;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Equations that = (Equations)o;
    return stable == that.stable && results.equals(that.results);
  }

  @Override
  public int hashCode() {
    return 31 * results.hashCode() + (stable ? 1 : 0);
  }

  Optional<Result> find(Direction direction) {
    int key = direction.asInt();
    for (DirectionResultPair result : results) {
      if (result.directionKey == key) {
        return Optional.of(result).map(pair -> pair.result);
      }
    }
    return Optional.empty();
  }
}
