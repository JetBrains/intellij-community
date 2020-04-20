// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.agent;

import com.intellij.debugger.engine.ReferringObject;
import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public abstract class MemoryAgentReferenceInfo {
  public enum ReferenceKind {
    CLASS(1),
    FIELD(2),
    ARRAY_ELEMENT(3),
    CLASS_LOADER(4),
    SIGNERS(5),
    PROTECTION_DOMAIN(6),
    INTERFACE(7),
    STATIC_FIELD(8),
    CONSTANT_POOL(9),
    SUPERCLASS(10),
    JNI_GLOBAL(21),
    SYSTEM_CLASS(22),
    MONITOR(23),
    STACK_LOCAL(24),
    JNI_LOCAL(25),
    THREAD(26),
    OTHER(27);

    private int value;

    private static final Map<Integer, ReferenceKind> INT_TO_REFERENCE_KIND = new HashMap<>();
    static {
      for (ReferenceKind kind : ReferenceKind.values()) {
        INT_TO_REFERENCE_KIND.put(kind.value, kind);
      }
    }

    ReferenceKind(int value) {
      this.value = value;
    }

    public static ReferenceKind valueOf(int value) {
      return INT_TO_REFERENCE_KIND.get(value);
    }
  }

  @NotNull protected final ReferenceKind kind;
  @NotNull protected final ObjectReference referrer;

  public MemoryAgentReferenceInfo(@NotNull ObjectReference referrer,
                                  @NotNull ReferenceKind kind) {
    this.referrer = referrer;
    this.kind = kind;
  }

  public @NotNull ReferenceKind getKind() { return kind; }

  public @NotNull ObjectReference getReferrer() { return referrer; }

  public abstract @NotNull ReferringObject createReferringObject();
}
