// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.messages.impl;

import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusFactory;
import org.jetbrains.annotations.NotNull;

public final class MessageBusFactoryImpl extends MessageBusFactory {
  @NotNull
  @Override
  public MessageBus createMessageBus(@NotNull Object owner) {
    return new MessageBusImpl.RootBus(owner);
  }

  @NotNull
  @Override
  public MessageBus createMessageBus(@NotNull Object owner, @NotNull MessageBus parentBus) {
    return new MessageBusImpl(owner, (MessageBusImpl)parentBus);
  }
}
