// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.function.Function;

/**
 * @author Alexander Lobas
 */
public interface FullScreeSupport {
  boolean isFullScreen();

  void addListener(@NotNull Window window);

  void removeListener(@NotNull Window window);

  Function<String, FullScreeSupport> NEW = className -> {
    try {
      return (FullScreeSupport)Class.forName(className).getConstructor().newInstance();
    }
    catch (Throwable e) {
      Logger.getInstance(FullScreeSupport.class).error(e);
    }
    return null;
  };
}