// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull
  @Override
  protected String getAdditionalInfo() {
    return String.format("in method: \"%s\" TID: %d DEPTH: %d", myMethodName, myTid, myDepth);
  }
}
