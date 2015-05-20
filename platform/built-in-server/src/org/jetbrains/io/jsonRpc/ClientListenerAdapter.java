package org.jetbrains.io.jsonRpc;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public abstract class ClientListenerAdapter implements ClientListener {
  @Override
  public void connected(@NotNull Client client, @Nullable Map<String, List<String>> parameters) {
  }

  @Override
  public void disconnected(@NotNull Client client) {
  }
}
