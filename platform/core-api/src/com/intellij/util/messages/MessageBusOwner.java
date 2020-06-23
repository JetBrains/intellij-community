// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
