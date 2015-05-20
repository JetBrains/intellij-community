package org.jetbrains.io.jsonRpc;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public interface MessageServer {
  void messageReceived(@NotNull Client client, @NotNull CharSequence message, boolean isBinary) throws IOException;
}