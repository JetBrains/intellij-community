// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 */
public final class DisableGC implements ModifiableEvaluator {
  private final Evaluator myDelegate;

  private DisableGC(@NotNull Evaluator delegate) {
    myDelegate = delegate;
  }

  public static Evaluator create(@NotNull Evaluator delegate) {
    if (!(delegate instanceof DisableGC)) {
      return new DisableGC(delegate);
    }
    return delegate;
  }

  @Override
  public @NotNull ModifiableValue evaluateModifiable(EvaluationContextImpl context) throws EvaluateException {
    if (myDelegate instanceof ModifiableEvaluator modifiableEvaluator) {
      ModifiableValue result = modifiableEvaluator.evaluateModifiable(context);
      Object value = result.getValue();
      keep(context, value);
      return result;
    }
    else {
      Object value = myDelegate.evaluate(context);
      keep(context, value);
      return new ModifiableValue(value, null);
    }
  }

  private static void keep(EvaluationContextImpl context, Object value) {
    if (value instanceof ObjectReference reference) {
      context.keep(reference);
    }
  }

  @Override
  public Modifier getModifier() {
    return myDelegate.getModifier();
  }

  @Override
  public String toString() {
    return "NoGC -> " + myDelegate;
  }

  public static Evaluator unwrap(Evaluator evaluator) {
    return evaluator instanceof DisableGC ? ((DisableGC)evaluator).myDelegate : evaluator;
  }
}
