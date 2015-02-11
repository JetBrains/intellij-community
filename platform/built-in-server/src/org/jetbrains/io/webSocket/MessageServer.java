package org.jetbrains.io.webSocket;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public interface MessageServer {
  void message(@NotNull Client client, String message) throws IOException;
}