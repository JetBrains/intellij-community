// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.junit;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.testframework.AbstractJavaTestConfigurationProducer;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated Use {@link JavaRunConfigurationProducerBase} instead
 */
@Deprecated(forRemoval = true)
public abstract class JavaRuntimeConfigurationProducerBase extends RuntimeConfigurationProducer {
  protected JavaRuntimeConfigurationProducerBase(@NotNull ConfigurationType configurationType) {
    super(configurationType);
  }

  @Nullable
  public static PsiPackage checkPackage(final PsiElement element) {
    return AbstractJavaTestConfigurationProducer.checkPackage(element);
  }

  protected boolean setupConfigurationModule(@Nullable ConfigurationContext context, ModuleBasedConfiguration configuration) {
    if (context != null) {
      final RunnerAndConfigurationSettings template =
        ((RunManagerImpl)context.getRunManager()).getConfigurationTemplate(getConfigurationFactory());
      final Module contextModule = context.getModule();
      final Module predefinedModule = ((ModuleBasedConfiguration<?, ?>)template.getConfiguration()).getConfigurationModule().getModule();
      if (predefinedModule != null) {
        configuration.setModule(predefinedModule);
        return true;
      }
      final Module module = findModule(configuration, contextModule);
      if (module != null) {
        configuration.setModule(module);
        return true;
      }
    }
    return false;
  }
  
  protected Module findModule(ModuleBasedConfiguration configuration, Module contextModule) {
    if (configuration.getConfigurationModule().getModule() == null && contextModule != null) {
      return contextModule;
    }
    return null;
  }
}
