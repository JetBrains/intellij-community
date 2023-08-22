// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull
  @Override
  protected String getAdditionalInfo() {
    return String.format("TID: %d DEPTH: %d", myTid, myDepth);
  }
}
