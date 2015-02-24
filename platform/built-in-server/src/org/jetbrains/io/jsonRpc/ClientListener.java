package org.jetbrains.io.jsonRpc;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EventListener;
import java.util.List;
import java.util.Map;

public interface ClientListener extends EventListener {
  void connected(@NotNull Client client, @Nullable Map<String, List<String>> parameters);

  void disconnected(@NotNull Client client);
}
