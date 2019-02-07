// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.agent.parsers;

import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;

public interface ResultParser<T> {
  T parse(@NotNull Value value);
}
