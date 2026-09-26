// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * An empty LocMessage
 */
final class EmptyMessage implements LocMessage {
  static final EmptyMessage INSTANCE = new EmptyMessage();
  private final PrefixSuffix EMPTY = new PrefixSuffix("", "");
  
  private EmptyMessage() {}

  @Override
  public @NotNull @Nls String label() {
    return "";
  }

  @Override
  public @NotNull PrefixSuffix splitLabel() {
    return EMPTY;
  }
}
