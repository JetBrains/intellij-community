// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.agent;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StackLocalReferringObject extends GCRootReferringObject {
  private final long tid;
  private final long depth;
  private final String methodName;

  public StackLocalReferringObject(@NotNull MemoryAgentReferenceKind kind,
                                   String methodName,
                                   long tid, long depth) {
    super(kind);
    this.tid = tid;
    this.methodName = methodName;
    this.depth = depth;
  }

  @NotNull
  @Override
  protected String getAdditionalInfo() {
    return String.format("%sTID: %d DEPTH: %d",
                         methodName != null ? String.format("from method: \"%s\" ", methodName) : "",
                         tid, depth);
  }
}
