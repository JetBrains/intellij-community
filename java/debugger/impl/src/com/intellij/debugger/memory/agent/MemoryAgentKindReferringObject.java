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
    switch (myKind) {
      case PROTECTION_DOMAIN:
        return "<protection domain>";
      case INTERFACE:
        return "<interface>";
      case SIGNERS:
        return "<signers>";
      case CLASS_LOADER:
        return "<loader>";
      case CLASS:
        return "<class>";
      case STATIC_FIELD:
        return "<static field>";
      case SUPERCLASS:
        return "<superclass>";
      default:
        return "Referrer " + order;
    }
  }
}
