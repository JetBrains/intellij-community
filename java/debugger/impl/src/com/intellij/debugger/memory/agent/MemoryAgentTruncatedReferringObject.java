// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.memory.agent;

import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;

public class MemoryAgentTruncatedReferringObject extends MemoryAgentSimpleReferringObject {
  private final int myLengthToStartObject;

  public MemoryAgentTruncatedReferringObject(@NotNull ObjectReference reference,
                                             boolean isWeakSoftReachable,
                                             int lengthToStartObject) {
    super(reference, isWeakSoftReachable);
    this.myLengthToStartObject = lengthToStartObject;
  }

  @Override
  public @NotNull String getNodeName(int order) {
    return String.format("... (%d referrers more)", myLengthToStartObject);
  }

  @Override
  public @NotNull String getSeparator() { return " from "; }
}
