// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.agent.extension;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

@ApiStatus.Internal
public final class JsonUtil {

  private static final @NotNull ObjectMapper MAPPER;

  static {
    MAPPER = new ObjectMapper();
    MAPPER.configOverride(ArrayNode.class)
      .setMergeable(true);
  }

  private JsonUtil() {
  }

  public static @NotNull String merge(@NotNull String oldJson, @NotNull String newJson) throws JsonProcessingException {
    if (oldJson.isEmpty()) {
      return newJson;
    }
    if (newJson.isEmpty()) {
      return oldJson;
    }
    Map old = MAPPER.readValue(oldJson, Map.class);
    ObjectReader updater = MAPPER.readerForUpdating(old);
    Map merged = updater.readValue(newJson);
    return MAPPER.writerWithDefaultPrettyPrinter()
      .writeValueAsString(merged);
  }
}
