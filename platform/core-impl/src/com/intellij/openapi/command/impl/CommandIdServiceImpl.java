// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicLong;


@ApiStatus.Experimental
@ApiStatus.Internal
public class CommandIdServiceImpl implements CommandIdService {

  // for debug: command - even number, transparent - odd number
  private final AtomicLong idGenerator = new AtomicLong();

  @Override
  public final void _advanceCommandId() {
    idGenerator.updateAndGet(id -> (id % 2 == 0) ? (id + 2) : (id + 1));
  }

  @Override
  public final void _advanceTransparentCommandId() {
    idGenerator.updateAndGet(id -> (id % 2 != 0) ? (id + 2) : (id + 1));
  }

  @Override
  public final @NotNull CommandId _currCommandId() {
    return new CommandIdImpl(currentId());
  }

  protected long currentId() {
    return idGenerator.get();
  }
}
