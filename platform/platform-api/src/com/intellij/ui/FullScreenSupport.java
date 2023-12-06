// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.function.Function;

/**
 * @author Alexander Lobas
 */
public interface FullScreenSupport {
  boolean isFullScreen();

  void addListener(@NotNull Window window);

  void removeListener(@NotNull Window window);

  Function<String, FullScreenSupport> NEW = className -> {
    try {
      return (FullScreenSupport)Class.forName(className).getConstructor().newInstance();
    }
    catch (Throwable e) {
      Logger.getInstance(FullScreenSupport.class).error(e);
    }
    return null;
  };
}