// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution;

import com.intellij.execution.configurations.*;
import com.intellij.execution.vmOptions.VMOption;
import com.intellij.execution.wsl.WslPath;
import com.intellij.java.codeserver.core.JavaPsiModuleUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.platform.eel.provider.LocalEelDescriptor;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.impl.light.LightJavaModule;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

import static com.intellij.platform.eel.provider.EelProviderUtil.getEelDescriptor;

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

  public @Nullable String findJavaModuleName(boolean inTests) {
    Module module = getConfigurationModule().getModule();
    if (module == null) return null;
    PsiJavaModule javaModule = JavaPsiModuleUtil.findDescriptorByModule(module, inTests);
    if (javaModule == null) return null;
    if (javaModule instanceof LightJavaModule) return null;
    return javaModule.getName();
  }

  private boolean jdkHomeSatisfies(Predicate<String> condition) {
    String path = getAlternativeJrePath();
    if (path != null) {
      Sdk sdk = ProjectJdkTable.getInstance().findJdk(path);
      if (sdk != null) {
        String homePath = sdk.getHomePath();
        if (homePath != null) {
          return condition.test(homePath);
        }
      }
      return condition.test(path);
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
      return sdkHomePath != null && condition.test(sdkHomePath);
    }
    return false;
  }

  protected boolean runsUnderRemoteJdk() {
    return jdkHomeSatisfies(homePath -> getEelDescriptor(Path.of(homePath)) != LocalEelDescriptor.INSTANCE);
  }

  @ApiStatus.Obsolete
  // TODO: use only runsUnderRemoteJdk
  protected boolean runsUnderWslJdk() {
    return jdkHomeSatisfies(WslPath::isWslUncPath);
  }

  @Override
  public List<ModuleBasedConfigurationOptions.ClasspathModification> getClasspathModifications() {
    return getOptions().getClasspathModifications();
  }

  @Override
  public void setClasspathModifications(List<ModuleBasedConfigurationOptions.ClasspathModification> modifications) {
    getOptions().setClasspathModifications(modifications);
  }

  /**
   * @return list of configuration-specific VM options (usually, -D options), used for completion
   */
  public List<VMOption> getKnownVMOptions() {
    return List.of(
      VMOption.property("java.awt.headless", "bool", "Run the application in headless mode", null),
      VMOption.property("user.home", "string", "User home directory", null),
      VMOption.property("user.dir", "string", "User working directory", null),
      VMOption.property("user.name", "string", "User account name", null)
    );
  }
}
