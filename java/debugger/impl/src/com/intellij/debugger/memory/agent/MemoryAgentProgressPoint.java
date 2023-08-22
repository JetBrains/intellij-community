// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.agent;

import com.google.gson.Gson;
import com.intellij.openapi.util.InvalidDataException;
import org.jetbrains.annotations.NotNull;

import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class MemoryAgentProgressPoint {
  private final String message;
  private final int minValue;
  private final int maxValue;
  private final int currentValue;

  private MemoryAgentProgressPoint(@NotNull String message, int minValue, int maxValue, int currentValue) {
    this.message = message;
    this.minValue = minValue;
    this.maxValue = maxValue;
    this.currentValue = currentValue;
  }

  @NotNull
  public static MemoryAgentProgressPoint fromJson(@NotNull String fileName) throws IOException {
    Gson gson = new Gson();
    MemoryAgentProgressPoint progressPoint = gson.fromJson(new FileReader(fileName, StandardCharsets.UTF_8), MemoryAgentProgressPoint.class);
    if (!progressPoint.isValid()) {
      throw new InvalidDataException("Invalid format of progress point");
    }
    return progressPoint;
  }

  public boolean isFinished() {
    return maxValue == currentValue;
  }

  public String getMessage() {
    return message;
  }

  public double getFraction() {
    return ((double)currentValue) / (maxValue - minValue);
  }

  private boolean isValid() {
    return maxValue > minValue && currentValue >= minValue && currentValue <= maxValue;
  }
}
