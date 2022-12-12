// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.messages.impl;

import com.intellij.util.ArrayUtilRt
import com.intellij.util.messages.SimpleMessageBusConnection

internal class SimpleMessageBusConnectionImpl(bus: MessageBusImpl) : BaseBusConnection(bus), SimpleMessageBusConnection {
  override fun disconnect() {
    val bus = this.bus ?: return;

    this.bus = null;
    // reset as bus will not remove disposed connection from list immediately
    bus.notifyConnectionTerminated(subscriptions.getAndSet(ArrayUtilRt.EMPTY_OBJECT_ARRAY));
  }
}
