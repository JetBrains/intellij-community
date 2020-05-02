// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.messages;

import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class MessageBusFactory {
  public static MessageBusFactory getInstance() {
    return ServiceManager.getService(MessageBusFactory.class);
  }

  public abstract @NotNull MessageBus createMessageBus(@NotNull MessageBusOwner owner);
  public abstract @NotNull MessageBus createMessageBus(@NotNull MessageBusOwner owner, @NotNull MessageBus parentBus);

  public static @NotNull MessageBus newMessageBus(@NotNull MessageBusOwner owner) {
    return getInstance().createMessageBus(owner);
  }

  public static @NotNull MessageBus newMessageBus(@NotNull MessageBusOwner owner, @Nullable MessageBus parentBus) {
    return parentBus == null ? newMessageBus(owner) : getInstance().createMessageBus(owner, parentBus);
  }
}