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
import com.intellij.execution.*;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.junit.JavaRunConfigurationProducerBase;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
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
import com.intellij.util.containers.HashSet;

import java.io.IOException;
import java.util.*;

public abstract class TestDiscoveryConfigurationProducer extends JavaRunConfigurationProducerBase<JavaTestConfigurationBase> {
  protected TestDiscoveryConfigurationProducer(ConfigurationType type) {
    super(type);
  }


  protected abstract void setPosition(JavaTestConfigurationBase configuration, PsiLocation<PsiMethod> position);
  protected abstract Pair<String, String> getPosition(JavaTestConfigurationBase configuration);

  @Override
  protected boolean setupConfigurationFromContext(final JavaTestConfigurationBase configuration,
                                                  ConfigurationContext configurationContext,
                                                  Ref<PsiElement> ref) {
    if (!Registry.is("testDiscovery.enabled")) {
      return false;
    }
    final Location contextLocation = configurationContext.getLocation();
    assert contextLocation != null;
    final Location location = JavaExecutionUtil.stepIntoSingleClass(contextLocation);
    if (location == null) return false;
    final PsiMethod sourceMethod = getSourceMethod(location);
    final Pair<String, String> position = getPosition(sourceMethod);
    if (sourceMethod != null && position != null) {
      try {
        final Project project = configuration.getProject();
        final TestDiscoveryIndex testDiscoveryIndex = TestDiscoveryIndex.getInstance(project);
        final Collection<String> testsByMethodName = testDiscoveryIndex.getTestsByMethodName(position.first, position.second);
        if (testsByMethodName == null ||
            ContainerUtil.filter(testsByMethodName, s -> s.startsWith(configuration.getFrameworkPrefix())).isEmpty()) {
          return false;
        }
        setPosition(configuration, new PsiLocation<>(sourceMethod));
        configuration.setName("Tests for " + StringUtil.getShortName(position.first) + "." + position.second);

        final RunnerAndConfigurationSettings template =
          configurationContext.getRunManager().getConfigurationTemplate(getConfigurationFactory());
        final Module predefinedModule = ((ModuleBasedConfiguration)template.getConfiguration()).getConfigurationModule().getModule();
        if (predefinedModule != null) {
          configuration.setModule(predefinedModule);
        }

        //potentially this set won't be big, it reflects modules from where user starts his tests
        final Collection<String> modules = testDiscoveryIndex.getTestModulesByMethodName(position.first,
                                                                                         position.second,
                                                                                         configuration.getFrameworkPrefix());
        if (modules.isEmpty()) return true;

        final List<Module> survivedModules = new ArrayList<>();
        final ModuleManager moduleManager = ModuleManager.getInstance(project);
        for (String moduleName : modules) {
          final Module moduleByName = moduleManager.findModuleByName(moduleName);
          if (moduleByName != null) {
            survivedModules.add(moduleByName);
          }
        }
        if (survivedModules.isEmpty()) return true;

        final Set<Module> allModules = new HashSet<>(Arrays.asList(moduleManager.getModules()));
        survivedModules
          .forEach(module -> {
            final List<Module> dependentModules = ModuleUtilCore.getAllDependentModules(module);
            dependentModules.add(module);
            allModules.retainAll(dependentModules);
          });
        if (!allModules.isEmpty()) {
          Module aModule = allModules.iterator().next();
          for (Module module : survivedModules) {
            if (allModules.contains(module)) {
              aModule = module;
            }
          }
          configuration.setModule(aModule);
        }

        return true;
      }
      catch (IOException e) {
        return false;
      }
    }
    return false;
  }

  @Override
  protected Module findModule(JavaTestConfigurationBase configuration, Module contextModule) {
    return null;
  }

  private static PsiMethod getSourceMethod(Location location) {
    final PsiElement psiElement = location.getPsiElement();
    final PsiMethod psiMethod = PsiTreeUtil.getParentOfType(psiElement, PsiMethod.class);
    if (psiMethod != null) {
      final PsiClass containingClass = psiMethod.getContainingClass();
      if (containingClass != null) {
        final TestFramework testFramework = TestFrameworks.detectFramework(containingClass);
        if (testFramework != null) {
          return null;
        }
        return psiMethod;
      }
    }
    return null;
  }

  private static Pair<String, String> getPosition(PsiMethod method) {
    if (method == null) {
      return null;
    }
    final PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) {
      return null;
    }
    final String qualifiedName = containingClass.getQualifiedName();
    if (qualifiedName != null) {
      return Pair.create(qualifiedName, method.getName());
    }
    return null;
  }

  @Override
  public boolean isConfigurationFromContext(JavaTestConfigurationBase configuration, ConfigurationContext configurationContext) {
    final Pair<String, String> position = getPosition(getSourceMethod(configurationContext.getLocation()));
    return position != null && position.equals(getPosition(configuration));
  }
}
