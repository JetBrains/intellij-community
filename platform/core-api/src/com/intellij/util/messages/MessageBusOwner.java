// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.messages;

import com.intellij.util.messages.impl.PluginListenerDescriptor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public interface MessageBusOwner {

  @ApiStatus.Internal
  @NotNull Object createListener(@NotNull PluginListenerDescriptor descriptor);

  @ApiStatus.Internal
  boolean isDisposed();

  @ApiStatus.Internal
  default boolean isParentLazyListenersIgnored() {
    return false;
  }
}
