/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.util.PairProcessor;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Gregory.Shrago
 */
public abstract class PatternConditionPlus<Target, Value> extends PatternCondition<Target> implements PairProcessor<Value, ProcessingContext> {
  private final ElementPattern myValuePattern;

  public PatternConditionPlus(@NonNls String methodName, final ElementPattern valuePattern) {
    super(methodName);
    myValuePattern = valuePattern;
  }

  public ElementPattern getValuePattern() {
    return myValuePattern;
  }

  public abstract boolean processValues(final Target t, final ProcessingContext context, final PairProcessor<Value, ProcessingContext> processor);

  public boolean accepts(@NotNull final Target t, final ProcessingContext context) {
    return !processValues(t, context, this);
  }

  public final boolean process(Value p, ProcessingContext context) {
    return !myValuePattern.getCondition().accepts(p, context);
  }
}
