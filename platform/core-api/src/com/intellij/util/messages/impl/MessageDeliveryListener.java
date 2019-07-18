// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.messages.impl;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@FunctionalInterface
@ApiStatus.Internal
public interface MessageDeliveryListener {
  void messageDelivered(@NotNull Topic topic, @NotNull String messageName, @NotNull Object handler, long durationNanos);
}
