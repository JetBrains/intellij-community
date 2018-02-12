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
package com.intellij.debugger.engine.evaluation;

import com.intellij.debugger.EvaluatingComputable;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author egor
 */
public class DebuggerComputableValue {
  private boolean myComputed;
  @Nullable private Value myValue;
  @Nullable private EvaluateException myException;

  @NotNull private final EvaluatingComputable<Value> myComputable;

  public DebuggerComputableValue(@NotNull EvaluatingComputable<Value> computable) {
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
