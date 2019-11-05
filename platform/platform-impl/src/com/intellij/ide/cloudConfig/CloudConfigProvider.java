// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.cloudConfig;

import com.intellij.ide.customize.AbstractCustomizeWizardStep;
import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.nio.file.Path;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public abstract class CloudConfigProvider {
  private static CloudConfigProvider myProvider;

  @Nullable
  public static CloudConfigProvider getProvider() {
    return myProvider;
  }

  public static void setProvider(@Nullable CloudConfigProvider provider) {
    myProvider = provider;
  }

  public abstract void initConfigsPanel(@NotNull ButtonGroup group, @NotNull JRadioButton customButton);

  public abstract void importFinished(@NotNull Path newConfigDir);

  public abstract void beforeStartupWizard();

  @Nullable
  public abstract String getLafClassName();

  @NotNull
  public abstract List<PluginId> getInstalledPlugins();

  public abstract int initSteps(@NotNull List<AbstractCustomizeWizardStep> steps);

  public abstract void startupWizardFinished();
}