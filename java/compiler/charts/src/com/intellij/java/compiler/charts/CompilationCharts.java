// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.compiler.charts;

import com.intellij.java.compiler.charts.events.ChartEvent;
import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;

public interface CompilationCharts extends Disposable {
  @NotNull JComponent getComponent();

  void put(@NotNull ChartEvent event);

  default void putAll(@NotNull Collection<? extends ChartEvent> events) {
    events.forEach(event -> put(event));
  }
}
