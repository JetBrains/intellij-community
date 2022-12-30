// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.agent;

import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MemoryAgentKindReferringObject extends MemoryAgentSimpleReferringObject {
  private final MemoryAgentReferenceKind myKind;

  public MemoryAgentKindReferringObject(@NotNull ObjectReference reference,
                                          boolean isWeakSoftReachable,
                                          @NotNull MemoryAgentReferenceKind kind) {
    super(reference, isWeakSoftReachable);
    this.myKind = kind;
  }

  @Nullable
  @Override
  public String getNodeName(int order) {
    return switch (myKind) {
      case PROTECTION_DOMAIN -> "<protection domain>";
      case INTERFACE -> "<interface>";
      case SIGNERS -> "<signers>";
      case CLASS_LOADER -> "<loader>";
      case CLASS -> "<class>";
      case STATIC_FIELD -> "<static field>";
      case SUPERCLASS -> "<superclass>";
      default -> "Referrer " + order;
    };
  }
}
