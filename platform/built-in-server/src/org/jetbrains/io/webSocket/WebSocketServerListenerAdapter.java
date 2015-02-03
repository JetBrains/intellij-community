package org.jetbrains.io.webSocket;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public abstract class WebSocketServerListenerAdapter implements WebSocketServerListener {
  @Override
  public void connected(@NotNull Client client, Map<String, List<String>> parameters) {
  }

  @Override
  public void disconnected(@NotNull Client client) {
  }
}
