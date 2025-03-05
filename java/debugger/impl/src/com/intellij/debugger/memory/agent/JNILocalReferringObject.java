// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.memory.agent;

import org.jetbrains.annotations.NotNull;

public class JNILocalReferringObject extends GCRootReferringObject {
  private final long myTid;
  private final long myDepth;

  public JNILocalReferringObject(@NotNull MemoryAgentReferenceKind kind,
                                 long tid, long depth) {
    super(kind);
    this.myTid = tid;
    this.myDepth = depth;
  }

  @Override
  protected @NotNull String getAdditionalInfo() {
    return String.format("TID: %d DEPTH: %d", myTid, myDepth);
  }
}
