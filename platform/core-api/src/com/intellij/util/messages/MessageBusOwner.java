// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.messages;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface MessageBusOwner {
  @NotNull Object createListener(@NotNull ListenerDescriptor descriptor);

  boolean isDisposed();

  default boolean isParentLazyListenersIgnored() {
    return false;
  }
}
