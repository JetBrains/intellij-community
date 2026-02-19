// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  public abstract boolean processValues(final Target t, final ProcessingContext context, final PairProcessor<? super Value, ? super ProcessingContext> processor);

  @Override
  public boolean accepts(final @NotNull Target t, final ProcessingContext context) {
    return !processValues(t, context, this);
  }

  @Override
  public final boolean process(Value p, ProcessingContext context) {
    return !myValuePattern.accepts(p, context);
  }
}
