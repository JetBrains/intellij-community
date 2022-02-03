// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.application;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.junit.JavaRunConfigurationProducerBase;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiMethodUtil;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public abstract class AbstractApplicationConfigurationProducer<T extends ApplicationConfiguration> extends JavaRunConfigurationProducerBase<T> {
  public AbstractApplicationConfigurationProducer() {
    super();
  }

  @Override
  protected boolean setupConfigurationFromContext(@NotNull T configuration,
                                                  @NotNull ConfigurationContext context,
                                                  @NotNull Ref<PsiElement> sourceElement) {
    final Location<?> contextLocation = context.getLocation();
    if (contextLocation == null) {
      return false;
    }
    final Location<?> location = JavaExecutionUtil.stepIntoSingleClass(contextLocation);
    if (location == null) {
      return false;
    }
    final PsiElement element = location.getPsiElement();
    if (!element.isPhysical()) {
      return false;
    }
    final PsiClass aClass = ApplicationConfigurationType.getMainClass(element);
    if (aClass == null) {
      return false;
    }
    PsiFile containingFile = aClass.getContainingFile();
    if (JavaHighlightUtil.isJavaHashBangScript(containingFile)) {
      return false;
    }
    PsiMethod method = PsiMethodUtil.findMainInClass(aClass);
    if (method != null && PsiTreeUtil.isAncestor(method, element, false)) {
      sourceElement.set(method);
    }
    else {
      sourceElement.set(aClass);
    }

    setupConfiguration(configuration, aClass, context);
    return true;
  }

  private void setupConfiguration(T configuration, final PsiClass aClass, final ConfigurationContext context) {
    configuration.setMainClassName(JavaExecutionUtil.getRuntimeQualifiedName(aClass));
    configuration.setGeneratedName();
    setupConfigurationModule(context, configuration);
  }

  @Override
  public boolean isConfigurationFromContext(@NotNull T appConfiguration, @NotNull ConfigurationContext context) {
    final PsiElement location = context.getPsiLocation();
    final PsiClass aClass = ApplicationConfigurationType.getMainClass(location);
    if (aClass != null && Objects.equals(JavaExecutionUtil.getRuntimeQualifiedName(aClass), appConfiguration.getMainClassName())) {
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
