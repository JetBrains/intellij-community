// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.patterns;

import com.intellij.util.SmartList;

import java.util.Collections;
import java.util.List;

@SuppressWarnings("ForLoopReplaceableByForEach")
public final class ElementPatternCondition<T> {

  private final InitialPatternCondition<T> myInitialCondition;
  private final List<PatternCondition<? super T>> myConditions;

  public ElementPatternCondition(final InitialPatternCondition<T> startCondition) {
    myInitialCondition = startCondition;
    myConditions = Collections.emptyList();
  }

  ElementPatternCondition(InitialPatternCondition<T> initialCondition, List<PatternCondition<? super T>> conditions) {
    myInitialCondition = initialCondition;
    myConditions = conditions;
  }

  private ElementPatternCondition(ElementPatternCondition<T> original, PatternCondition<? super T> condition) {
    myInitialCondition = original.getInitialCondition();
    myConditions = new SmartList<>(original.getConditions());
    myConditions.add(condition);
  }

  public String toString() {
    StringBuilder builder = new StringBuilder();
    append(builder, "");
    return builder.toString();
  }

  public void append(StringBuilder builder, String indent) {
    myInitialCondition.append(builder, indent);
    final int conditionSize = myConditions.size();

    for (int i = 0; i < conditionSize; ++i) { // for each is slower
      final PatternCondition<? super T> condition = myConditions.get(i);
      condition.append(builder.append(".\n").append(indent), indent);
    }
  }

  public List<PatternCondition<? super T>> getConditions() {
    return myConditions;
  }

  public InitialPatternCondition<T> getInitialCondition() {
    return myInitialCondition;
  }

  public ElementPatternCondition<T> append(PatternCondition<? super T> condition) {
    return new ElementPatternCondition<>(this, condition);
  }
}
