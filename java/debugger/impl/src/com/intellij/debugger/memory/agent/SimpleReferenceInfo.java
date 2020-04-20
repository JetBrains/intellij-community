// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.agent;

import com.intellij.debugger.engine.ReferringObject;
import com.intellij.debugger.engine.SimpleReferringObject;
import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;

public class SimpleReferenceInfo extends MemoryAgentReferenceInfo {
  public SimpleReferenceInfo(@NotNull ObjectReference referrer,
                             @NotNull ReferenceKind kind) {
    super(referrer, kind);
  }

  @NotNull
  @Override
  public ReferringObject createReferringObject() { return new SimpleReferringObject(referrer); }
}
