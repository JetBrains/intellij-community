/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.execution.testDiscovery;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.junit.JavaRunConfigurationProducerBase;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testIntegration.TestFramework;
import com.intellij.util.containers.ContainerUtil;

import java.io.IOException;
import java.util.Collection;

public abstract class TestDiscoveryConfigurationProducer extends JavaRunConfigurationProducerBase<TestDiscoveryConfiguration> {
  protected TestDiscoveryConfigurationProducer(ConfigurationType type) {
    super(type);
  }

  @Override
  protected boolean setupConfigurationFromContext(final TestDiscoveryConfiguration configuration,
                                                  ConfigurationContext configurationContext,
                                                  Ref<PsiElement> ref) {
    if (!Registry.is("testDiscovery.enabled")) {
      return false;
    }
    final Location contextLocation = configurationContext.getLocation();
    assert contextLocation != null;
    final Location location = JavaExecutionUtil.stepIntoSingleClass(contextLocation);
    if (location == null) return false;
    final Pair<String, String> position = getPosition(location);
    if (position != null) {
      try {
        final Collection<String> testsByMethodName = TestDiscoveryIndex
          .getInstance(configuration.getProject()).getTestsByMethodName(position.first, position.second);
        if (testsByMethodName == null || ContainerUtil.filter(testsByMethodName, new Condition<String>() {
          @Override
          public boolean value(String s) {
            return s.startsWith(configuration.getFrameworkPrefix());
          }
        }).isEmpty()) return false;
        
      }
      catch (IOException e) {
        return false;
      }
      configuration.setPosition(position);
      configuration.setName("Tests for " + StringUtil.getShortName(position.first) + "." + position.second);
      setupPackageConfiguration(configurationContext, configuration, TestSearchScope.MODULE_WITH_DEPENDENCIES);
      return true;
    }
    return false;
  }

  @Override
  protected Module findModule(TestDiscoveryConfiguration configuration, Module contextModule) {
    return null;
  }

  private static Pair<String, String> getPosition(Location location) {
    final PsiElement psiElement = location.getPsiElement();
    final PsiMethod psiMethod = PsiTreeUtil.getParentOfType(psiElement, PsiMethod.class);
    if (psiMethod != null) {
      final PsiClass containingClass = psiMethod.getContainingClass();
      if (containingClass != null) {
        final TestFramework testFramework = TestFrameworks.detectFramework(containingClass);
        if (testFramework != null) {
          return null;
        }
        final String qualifiedName = containingClass.getQualifiedName();
        if (qualifiedName != null) {
          return Pair.create(qualifiedName, psiMethod.getName());
        }
      }
    }
    return null;
  }

  @Override
  public boolean isConfigurationFromContext(TestDiscoveryConfiguration configuration, ConfigurationContext configurationContext) {
    final Pair<String, String> position = getPosition(configurationContext.getLocation());
    return position != null && position.equals(configuration.getPosition());
  }
}
