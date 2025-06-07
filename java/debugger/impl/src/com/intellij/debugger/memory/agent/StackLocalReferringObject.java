// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.memory.agent;

import org.jetbrains.annotations.NotNull;

public class StackLocalReferringObject extends GCRootReferringObject {
  private final long myTid;
  private final long myDepth;
  private final String myMethodName;

  public StackLocalReferringObject(@NotNull MemoryAgentReferenceKind kind,
                                   @NotNull String methodName,
                                   long tid, long depth) {
    super(kind);
    this.myTid = tid;
    this.myMethodName = methodName;
    this.myDepth = depth;
  }

  @Override
  protected @NotNull String getAdditionalInfo() {
    return String.format("in method: \"%s\" TID: %d DEPTH: %d", myMethodName, myTid, myDepth);
  }
}
