// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.cloudConfig;

import com.intellij.openapi.wm.StatusBar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * @author Alexander Lobas
 */
public abstract class CloudConfigTestProvider {
  private static CloudConfigTestProvider myProvider;

  public static @Nullable CloudConfigTestProvider getProvider() {
    return myProvider;
  }

  public static void setProvider(@NotNull CloudConfigTestProvider provider) {
    myProvider = provider;
  }

  public abstract void initLocalClient(@NotNull File location);

  public abstract @NotNull String getStatusInfo();

  public abstract @NotNull String getPluginsInfo();

  public abstract @NotNull String getActions(@NotNull StatusBar statusBar);

  public abstract void runAction(@NotNull StatusBar statusBar, @NotNull String name);
}