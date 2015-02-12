package org.jetbrains.io.jsonRpc;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public interface MessageServer {
  void message(@NotNull Client client, @NotNull String message) throws IOException;
}