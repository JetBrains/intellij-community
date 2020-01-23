// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.cloudConfig;

import com.intellij.openapi.wm.StatusBar;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author Alexander Lobas
 */
public interface CloudConfigTestProvider {
  void initLocalClient(@NotNull File location);

  @NotNull String getStatusInfo();

  @NotNull String getActions(@NotNull StatusBar statusBar);

  void runAction(@NotNull StatusBar statusBar, @NotNull String name);
}