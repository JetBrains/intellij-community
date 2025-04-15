// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.patterns;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ElementPatternCondition<T> {
  private final InitialPatternCondition<T> initialCondition;
  private final List<PatternCondition<? super T>> conditions;

  public ElementPatternCondition(@NotNull InitialPatternCondition<T> startCondition) {
    initialCondition = startCondition;
    conditions = Collections.emptyList();
  }

  ElementPatternCondition(@NotNull InitialPatternCondition<T> initialCondition, @NotNull List<PatternCondition<? super T>> conditions) {
    this.initialCondition = initialCondition;
    this.conditions = conditions;
  }

  private ElementPatternCondition(@NotNull ElementPatternCondition<T> original, PatternCondition<? super T> condition) {
    initialCondition = original.getInitialCondition();
    conditions = new ArrayList<>(original.conditions.size() + 1);
    conditions.addAll(original.conditions);
    conditions.add(condition);
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    append(builder, "");
    return builder.toString();
  }

  public void append(StringBuilder builder, String indent) {
    initialCondition.append(builder, indent);
    int conditionSize = conditions.size();

    // for each it is slower
    for (int i = 0; i < conditionSize; ++i) {
      conditions.get(i).append(builder.append(".\n").append(indent), indent);
    }
  }

  public @Unmodifiable List<PatternCondition<? super T>> getConditions() {
    return conditions;
  }

  public InitialPatternCondition<T> getInitialCondition() {
    return initialCondition;
  }

  public ElementPatternCondition<T> append(PatternCondition<? super T> condition) {
    return new ElementPatternCondition<>(this, condition);
  }
}
