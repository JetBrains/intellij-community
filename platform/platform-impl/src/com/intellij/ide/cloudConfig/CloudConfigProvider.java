/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.cloudConfig;

import com.intellij.ide.customize.AbstractCustomizeWizardStep;
import com.intellij.openapi.application.ConfigImportSettings;
import com.intellij.openapi.application.ImportOldConfigsPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
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

  public abstract void initConfigsPanel(@NotNull ImportOldConfigsPanel dialog,
                                        @NotNull JPanel parentPanel,
                                        @NotNull ConfigImportSettings settings);

  public abstract void importFinished(@NotNull File newConfigDir);

  public abstract void beforeStartupWizard();

  @Nullable
  public abstract String getLafClassName();

  @NotNull
  public abstract List<String> getInstalledPlugins();

  public abstract int initSteps(@NotNull List<AbstractCustomizeWizardStep> steps);

  public abstract void startupWizardFinished();
}