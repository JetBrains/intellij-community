/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 */
public class DisableGC implements Evaluator {
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

  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    final Object result = myDelegate.evaluate(context);
    if (result instanceof ObjectReference) {
      context.getSuspendContext().keep((ObjectReference)result);
    }
    return result;
  }

  public Evaluator getDelegate() {
    return myDelegate;
  }

  public Modifier getModifier() {
    return myDelegate.getModifier();
  }

  @Override
  public String toString() {
    return "NoGC -> " + myDelegate;
  }
}
