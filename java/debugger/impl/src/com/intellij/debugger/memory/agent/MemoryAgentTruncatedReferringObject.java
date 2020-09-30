// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull
  @Override
  public String getNodeName(int order) {
    return String.format("... (%d referrers more)", myLengthToStartObject);
  }

  @NotNull
  @Override
  public String getSeparator() { return " from "; }
}
