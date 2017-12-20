/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.execution.application;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.ConfigurationUtil;
import com.intellij.execution.junit.JavaRunConfigurationProducerBase;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiMethodUtil;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractApplicationConfigurationProducer<T extends ApplicationConfiguration> extends JavaRunConfigurationProducerBase<T> {

  public AbstractApplicationConfigurationProducer(final ApplicationConfigurationType configurationType) {
    super(configurationType);
  }

  @Override
  protected boolean setupConfigurationFromContext(T configuration, ConfigurationContext context, Ref<PsiElement> sourceElement) {
    final Location contextLocation = context.getLocation();
    if (contextLocation == null) {
      return false;
    }
    final Location location = JavaExecutionUtil.stepIntoSingleClass(contextLocation);
    if (location == null) {
      return false;
    }
    final PsiElement element = location.getPsiElement();
    if (!element.isPhysical()) {
      return false;
    }
    PsiElement currentElement = element;
    PsiMethod method;
    while ((method = findMain(currentElement)) != null) {
      final PsiClass aClass = method.getContainingClass();
      if (ConfigurationUtil.MAIN_CLASS.value(aClass)) {
        sourceElement.set(method);
        setupConfiguration(configuration, aClass, context);
        return true;
      }
      currentElement = method.getParent();
    }
    final PsiClass aClass = ApplicationConfigurationType.getMainClass(element);
    if (aClass == null) {
      return false;
    }
    sourceElement.set(aClass);
    setupConfiguration(configuration, aClass, context);
    return true;
  }

  private void setupConfiguration(T configuration, final PsiClass aClass, final ConfigurationContext context) {
    configuration.setMainClassName(JavaExecutionUtil.getRuntimeQualifiedName(aClass));
    configuration.setGeneratedName();
    setupConfigurationModule(context, configuration);
  }

  @Nullable
  private static PsiMethod findMain(PsiElement element) {
    PsiMethod method;
    while ((method = PsiTreeUtil.getParentOfType(element, PsiMethod.class)) != null) {
      if (PsiMethodUtil.isMainMethod(method)) return method;
      else element = method.getParent();
    }
    return null;
  }

  @Override
  public boolean isConfigurationFromContext(T appConfiguration, ConfigurationContext context) {
    final PsiElement location = context.getPsiLocation();
    final PsiClass aClass = ApplicationConfigurationType.getMainClass(location);
    if (aClass != null && Comparing.equal(JavaExecutionUtil.getRuntimeQualifiedName(aClass), appConfiguration.getMainClassName())) {
      final PsiMethod method = PsiTreeUtil.getParentOfType(location, PsiMethod.class, false);
      if (method != null && TestFrameworks.getInstance().isTestMethod(method)) {
        return false;
      }

      final Module configurationModule = appConfiguration.getConfigurationModule().getModule();
      if (Comparing.equal(context.getModule(), configurationModule)) return true;

      ApplicationConfiguration template =
        (ApplicationConfiguration)context.getRunManager().getConfigurationTemplate(getConfigurationFactory()).getConfiguration();
      final Module predefinedModule = template.getConfigurationModule().getModule();
      if (Comparing.equal(predefinedModule, configurationModule)) {
        return true;
      }
    }
    return false;
  }
}
