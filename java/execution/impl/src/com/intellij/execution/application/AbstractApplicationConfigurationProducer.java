// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.psi.*;
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
    if (aClass instanceof PsiImplicitClass) {
      configuration.setImplicitClassConfiguration(true);
    }
    configuration.setMainClassName(JavaExecutionUtil.getRuntimeQualifiedName(aClass));
    configuration.setGeneratedName();
    setupConfigurationModule(context, configuration);
  }

  @Override
  public boolean isConfigurationFromContext(@NotNull T appConfiguration, @NotNull ConfigurationContext context) {
    Location<?> location = context.getLocation();
    if (location == null) {
      return false;
    }

    Location<?> singleClassLocation = JavaExecutionUtil.stepIntoSingleClass(location);
    final PsiClass aClass = PsiTreeUtil.getParentOfType(singleClassLocation.getPsiElement(), PsiClass.class, false);
    if (aClass != null) {
      final String className = JavaExecutionUtil.getRuntimeQualifiedName(aClass);
      if (!Objects.equals(className, appConfiguration.getMainClassName())) return false;

      final PsiMethod method = PsiTreeUtil.getParentOfType(context.getPsiLocation(), PsiMethod.class, false);
      if (method != null && TestFrameworks.getInstance().isTestMethod(method)) {
        return false;
      }

      final Module configurationModule = appConfiguration.getConfigurationModule().getModule();
      if (Comparing.equal(context.getModule(), configurationModule)) return true;

      ApplicationConfiguration template = (ApplicationConfiguration)context
        .getRunManager()
        .getConfigurationTemplate(getConfigurationFactory())
        .getConfiguration();
      final Module predefinedModule = template.getConfigurationModule().getModule();
      return Comparing.equal(predefinedModule, configurationModule);
    }
    return false;
  }
}
