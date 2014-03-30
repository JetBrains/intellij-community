/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

public class ApplicationConfigurationProducer extends JavaRunConfigurationProducerBase<ApplicationConfiguration> {

  public ApplicationConfigurationProducer() {
    super(ApplicationConfigurationType.getInstance());
  }

  @Override
  protected boolean setupConfigurationFromContext(ApplicationConfiguration configuration,
                                                  ConfigurationContext context,
                                                  Ref<PsiElement> sourceElement) {
    Location location = JavaExecutionUtil.stepIntoSingleClass(context.getLocation());
    if (location == null) return false;
    final PsiElement element = location.getPsiElement();
    if (!element.isPhysical()) return false;
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
    if (aClass == null) return false;
    sourceElement.set(aClass);
    setupConfiguration(configuration, aClass, context);
    return true;
  }

  private void setupConfiguration(ApplicationConfiguration configuration,
                                  final PsiClass aClass,
                                  final ConfigurationContext context) {
    configuration.MAIN_CLASS_NAME = JavaExecutionUtil.getRuntimeQualifiedName(aClass);
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
  public boolean isConfigurationFromContext(ApplicationConfiguration appConfiguration, ConfigurationContext context) {
    final PsiElement location = context.getPsiLocation();
    final PsiClass aClass = ApplicationConfigurationType.getMainClass(location);
    if (aClass != null && Comparing.equal(JavaExecutionUtil.getRuntimeQualifiedName(aClass), appConfiguration.MAIN_CLASS_NAME)) {
      final PsiMethod method = PsiTreeUtil.getParentOfType(location, PsiMethod.class, false);
      if (method != null && TestFrameworks.getInstance().isTestMethod(method)) {
        return false;
      }

      final Module configurationModule = appConfiguration.getConfigurationModule().getModule();
      if (Comparing.equal(context.getModule(), configurationModule)) return true;

      ApplicationConfiguration template = (ApplicationConfiguration)context.getRunManager()
        .getConfigurationTemplate(getConfigurationFactory())
        .getConfiguration();
      final Module predefinedModule = template.getConfigurationModule().getModule();
      if (Comparing.equal(predefinedModule, configurationModule)) {
        return true;
      }
    }
    return false;
  }
}
