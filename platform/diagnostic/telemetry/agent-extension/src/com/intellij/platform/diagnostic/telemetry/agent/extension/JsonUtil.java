// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.agent.extension;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;

import java.util.Map;

@ApiStatus.Internal
public final class JsonUtil {

  private static final @NotNull ObjectMapper MAPPER = JsonMapper.builder()
    .withConfigOverride(ArrayNode.class, o -> o.setMergeable(Boolean.TRUE))
    .build();

  private JsonUtil() {
  }

  public static @NotNull String merge(@NotNull String oldJson, @NotNull String newJson) throws JacksonException {
    if (oldJson.isEmpty()) {
      return newJson;
    }
    if (newJson.isEmpty()) {
      return oldJson;
    }
    Map<String, Object> old = MAPPER.readValue(oldJson, new TypeReference<Map<String, Object>>() { });
    ObjectReader updater = MAPPER.readerForUpdating(old);
    Map<String, Object> merged = updater.readValue(newJson);
    return MAPPER.writerWithDefaultPrettyPrinter()
      .writeValueAsString(merged);
  }
}
