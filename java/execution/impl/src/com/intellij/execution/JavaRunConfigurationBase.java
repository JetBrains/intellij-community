// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.JavaRunConfigurationModule;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.wsl.WslDistributionManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

public abstract class JavaRunConfigurationBase extends ModuleBasedConfiguration<JavaRunConfigurationModule, Element>
  implements CommonJavaRunConfigurationParameters, ConfigurationWithCommandLineShortener {

  public JavaRunConfigurationBase(String name,
                                  @NotNull JavaRunConfigurationModule configurationModule,
                                  @NotNull ConfigurationFactory factory) {
    super(name, configurationModule, factory);
  }

  public JavaRunConfigurationBase(@NotNull JavaRunConfigurationModule configurationModule,
                                  @NotNull ConfigurationFactory factory) {
    super(configurationModule, factory);
  }

  protected boolean runsUnderWslJdk() {
    String path = getAlternativeJrePath();
    if (path != null) {
      return WslDistributionManager.isWslPath(path);
    }
    Module module = getConfigurationModule().getModule();
    if (module != null) {
      Sdk sdk;
      try {
        sdk = JavaParameters.getValidJdkToRunModule(module, false);
      }
      catch (CantRunException e) {
        return false;
      }
      String sdkHomePath = sdk.getHomePath();
      return sdkHomePath != null && WslDistributionManager.isWslPath(sdkHomePath);
    }
    return false;
  }
}
