// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;


@ApiStatus.Experimental
@ApiStatus.Internal
public class CommandIdServiceImpl implements CommandIdService {

  // for debug: command - even number, transparent - odd number
  private final AtomicLong idGenerator = new AtomicLong();

  private final AtomicReference<CommandId> forcedCommandId = new AtomicReference<>();

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
    CommandId forcedCommand = forcedCommandId.get();
    if (forcedCommand != null) {
      return forcedCommand;
    }
    return CommandId.fromLong(currentId());
  }

  @Override
  public void _setForcedCommand(@Nullable CommandId commandId) {
    forcedCommandId.set(commandId);
  }

  protected long currentId() {
    return idGenerator.get();
  }
}
