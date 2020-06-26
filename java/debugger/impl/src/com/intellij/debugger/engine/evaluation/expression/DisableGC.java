// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 */
public final class DisableGC implements Evaluator {
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
  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    final Object result = myDelegate.evaluate(context);
    if (result instanceof ObjectReference) {
      context.getSuspendContext().keep((ObjectReference)result);
    }
    return result;
  }

  /**
   * @deprecated use {@link #unwrap(Evaluator)} instead
   */
  @Deprecated
  public Evaluator getDelegate() {
    return myDelegate;
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
