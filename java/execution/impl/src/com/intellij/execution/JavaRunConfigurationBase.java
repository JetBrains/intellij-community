// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.execution.configurations.*;
import com.intellij.execution.wsl.WslDistributionManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.List;

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

  @Override
  public List<ModuleBasedConfigurationOptions.ClasspathModification> getClasspathModifications() {
    return getOptions().getClasspathModifications();
  }

  @Override
  public void setClasspathModifications(List<ModuleBasedConfigurationOptions.ClasspathModification> modifications) {
    getOptions().setClasspathModifications(modifications);
  }
}
