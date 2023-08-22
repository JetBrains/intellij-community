// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.cloudConfig;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.nio.file.Path;

/**
 * @author Alexander Lobas
 */
public abstract class CloudConfigProvider {
  private static CloudConfigProvider myProvider;

  public static @Nullable CloudConfigProvider getProvider() {
    return myProvider;
  }

  public static void setProvider(@Nullable CloudConfigProvider provider) {
    myProvider = provider;
  }

  public abstract void initConfigsPanel(@NotNull ButtonGroup group, @NotNull JRadioButton customButton);

  public abstract void importFinished(@NotNull Path newConfigDir);

  public abstract @Nullable String getLafClassName();

  public abstract boolean importSettingsSilently(@NotNull Path newConfigDir);
}