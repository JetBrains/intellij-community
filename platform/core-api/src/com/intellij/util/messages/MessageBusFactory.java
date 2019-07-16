// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.util.messages;

import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class MessageBusFactory {
  public static MessageBusFactory getInstance() {
    return ServiceManager.getService(MessageBusFactory.class);
  }

  @NotNull
  public abstract MessageBus createMessageBus(@NotNull Object owner);
  @NotNull
  public abstract MessageBus createMessageBus(@NotNull Object owner, @NotNull MessageBus parentBus);

  @NotNull
  public static MessageBus newMessageBus(@NotNull Object owner) {
    return getInstance().createMessageBus(owner);
  }

  @NotNull
  public static MessageBus newMessageBus(@NotNull Object owner, @Nullable MessageBus parentBus) {
    return parentBus == null ? newMessageBus(owner) : getInstance().createMessageBus(owner, parentBus);
  }
}