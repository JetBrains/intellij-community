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
public interface SingleClassConfiguration {
  void setMainClass(final PsiClass psiClass);

  PsiClass getMainClass();
  void setMainClassName(String qualifiedName);
}
