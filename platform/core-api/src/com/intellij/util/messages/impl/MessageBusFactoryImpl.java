// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
      return createRootBus(owner);
    }

    CompositeMessageBus parent = (CompositeMessageBus)parentBus;
    if (parent.getParent() == null) {
      return new CompositeMessageBus(owner, parent);
    }
    else {
      return new MessageBusImpl(owner, parent);
    }
  }

  public static @NotNull MessageBusImpl.RootBus createRootBus(@NotNull MessageBusOwner owner) {
    return new MessageBusImpl.RootBus(owner);
  }
}
