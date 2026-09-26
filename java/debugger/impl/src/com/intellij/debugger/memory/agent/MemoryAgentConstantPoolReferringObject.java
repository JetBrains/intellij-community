// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.agent;

import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MemoryAgentConstantPoolReferringObject extends MemoryAgentSimpleReferringObject {
  private final int myIndex;

  public MemoryAgentConstantPoolReferringObject(@NotNull ObjectReference reference, int index) {
    super(reference, false);
    this.myIndex = index;
  }

  @Override
  public @Nullable String getNodeName(int order) {
    return String.format("<constant pool> [%d]", myIndex);
  }
}
