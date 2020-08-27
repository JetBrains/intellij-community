// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.agent;

import java.util.HashMap;
import java.util.Map;

public enum MemoryAgentReferenceKind {
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
  OTHER(27),
  TRUNCATE(42); // Fake reference to truncate paths

  private final int myValue;

  private static final Map<Integer, MemoryAgentReferenceKind> INT_TO_REFERENCE_KIND = new HashMap<>();
  static {
    for (MemoryAgentReferenceKind kind : values()) {
      INT_TO_REFERENCE_KIND.put(kind.myValue, kind);
    }
  }

  MemoryAgentReferenceKind(int value) {
    this.myValue = value;
  }

  public static MemoryAgentReferenceKind valueOf(int value) {
    return INT_TO_REFERENCE_KIND.get(value);
  }
}