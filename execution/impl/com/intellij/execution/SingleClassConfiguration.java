/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfigurationModule;
import com.intellij.psi.PsiClass;

/**
 * @author dyoma
 */
public abstract class SingleClassConfiguration extends ModuleBasedConfiguration  {
  public SingleClassConfiguration(final String name, final RunConfigurationModule configurationModule, final ConfigurationFactory factory) {
    super(name, configurationModule, factory);
  }

  public void setMainClass(final PsiClass psiClass) {
    setMainClassName(ExecutionUtil.getRuntimeQualifiedName(psiClass));
    setModule(ExecutionUtil.findModule(psiClass));
  }

  public abstract PsiClass getMainClass();
  public abstract void setMainClassName(String qualifiedName);
}
