// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.messages.impl;

import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusFactory;
import com.intellij.util.messages.MessageBusOwner;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class MessageBusFactoryImpl extends MessageBusFactory {
  @Override
  public @NotNull MessageBus createMessageBus(@NotNull MessageBusOwner owner, @Nullable MessageBus parentBus) {
    if (parentBus == null) {
      return new RootBus(owner);
    }

    CompositeMessageBus parent = (CompositeMessageBus)parentBus;
    if (parent.getParent() == null) {
      return new CompositeMessageBus(owner, parent);
    }
    else {
      return new MessageBusImpl(owner, parent);
    }
  }

  public static @NotNull RootBus createRootBus(@NotNull MessageBusOwner owner) {
    return new RootBus(owner);
  }
}
