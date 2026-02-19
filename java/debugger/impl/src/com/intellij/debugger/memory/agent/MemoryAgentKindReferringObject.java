// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  @Override
  public @Nullable String getNodeName(int order) {
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
