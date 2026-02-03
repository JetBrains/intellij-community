// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.messages;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class MessageBusFactory {
  public static MessageBusFactory getInstance() {
    return ApplicationManager.getApplication().getService(MessageBusFactory.class);
  }

  public abstract @NotNull MessageBus createMessageBus(@NotNull MessageBusOwner owner, @Nullable MessageBus parentBus);

  public static @NotNull MessageBus newMessageBus(@NotNull MessageBusOwner owner) {
    return getInstance().createMessageBus(owner, null);
  }
}