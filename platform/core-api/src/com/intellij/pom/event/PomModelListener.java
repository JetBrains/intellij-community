// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pom.event;

import com.intellij.pom.PomModelAspect;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

public interface PomModelListener extends EventListener {
  void modelChanged(@NotNull PomModelEvent event);

  boolean isAspectChangeInteresting(@NotNull PomModelAspect aspect);
}