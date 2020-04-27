// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine.evaluation;

import com.intellij.debugger.EvaluatingComputable;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DebuggerComputableValue {
  private boolean myComputed;
  @Nullable private Value myValue;
  @Nullable private EvaluateException myException;

  @NotNull private final EvaluatingComputable<? extends Value> myComputable;

  public DebuggerComputableValue(@NotNull EvaluatingComputable<? extends Value> computable) {
    myComputable = computable;
  }

  public DebuggerComputableValue(@Nullable Value value) {
    myComputed = true;
    myValue = value;
    myComputable = () -> value;
  }

  public static DebuggerComputableValue computed(Value value) {
    DebuggerComputableValue res = new DebuggerComputableValue(() -> value);
    res.myComputed = true;
    res.myValue = value;
    return res;
  }

  @Nullable
  public Value getValue() throws EvaluateException {
    if (!myComputed) {
      try {
        myValue = myComputable.compute();
      }
      catch (EvaluateException e) {
        myException = e;
      }
      myComputed = true;
    }
    if (myException != null) {
      throw myException;
    }
    return myValue;
  }
}
