// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.messages.impl;

import com.intellij.util.ArrayUtilRt;
import com.intellij.util.messages.SimpleMessageBusConnection;
import org.jetbrains.annotations.NotNull;

final class SimpleMessageBusConnectionImpl extends BaseBusConnection implements SimpleMessageBusConnection {
  SimpleMessageBusConnectionImpl(@NotNull MessageBusImpl bus) {
    super(bus);
  }

  @Override
  public void disconnect() {
    MessageBusImpl bus = this.bus;
    if (bus == null) {
      return;
    }

    this.bus = null;
    // reset as bus will not remove disposed connection from list immediately
    bus.notifyConnectionTerminated(subscriptions.getAndSet(ArrayUtilRt.EMPTY_OBJECT_ARRAY));
  }
}
