// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.reference;

import org.jetbrains.annotations.NotNull;

public interface WritableRefEntity extends RefEntity {
  void setOwner(@NotNull WritableRefEntity owner);

  void add(@NotNull RefEntity child);

  void removeChild(@NotNull RefEntity child);
}
