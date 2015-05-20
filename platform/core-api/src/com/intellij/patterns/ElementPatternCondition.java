/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.patterns;

import com.intellij.util.ProcessingContext;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.Nullable;

import java.lang.Object;
import java.util.Collections;
import java.util.List;

/**
 * @author peter
 */
@SuppressWarnings("ForLoopReplaceableByForEach")
public class ElementPatternCondition<T> {

  private final InitialPatternCondition<T> myInitialCondition;
  private final List<PatternCondition<? super T>> myConditions;

  public ElementPatternCondition(final InitialPatternCondition<T> startCondition) {
    myInitialCondition = startCondition;
    myConditions = Collections.emptyList();
  }

  protected ElementPatternCondition(final ElementPatternCondition<T> original,
                                    final PatternCondition<? super T> condition) {
    myInitialCondition = original.getInitialCondition();
    myConditions = new SmartList<PatternCondition<? super T>>(original.getConditions());
    myConditions.add(condition);
  }

  /**
   * @deprecated To remove in IDEA 15. Use {@link ElementPattern#accepts(Object, ProcessingContext)} instead.
   */
  @Deprecated
  public boolean accepts(@Nullable Object o, final ProcessingContext context) {
    if (!myInitialCondition.accepts(o, context)) return false;
    final int listSize = myConditions.size();
    for (int i = 0; i < listSize; i++) {
      if (!myConditions.get(i).accepts((T)o, context)) return false;
    }
    return true;
  }

  public final String toString() {
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
    return new ElementPatternCondition<T>(this, condition);
  }
}
