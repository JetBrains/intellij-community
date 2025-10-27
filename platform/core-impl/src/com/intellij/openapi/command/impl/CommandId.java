// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;


@ApiStatus.Experimental
@ApiStatus.Internal
public interface CommandId {

  static @NotNull CommandId fromLong(long id) {
    return new CommandIdImpl(id);
  }

  boolean isCompatible(@NotNull CommandId commandId);

  long asLong();
}
