package org.jetbrains.io.jsonRpc;

import org.jetbrains.annotations.NotNull;

public interface MessageServer {
  void messageReceived(@NotNull Client client, @NotNull CharSequence message);
}