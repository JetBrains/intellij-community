// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testDiscovery;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.execution.*;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.*;
import com.intellij.execution.junit.JavaRunConfigurationProducerBase;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.TestSearchScope;
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
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testIntegration.TestFramework;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public abstract class TestDiscoveryConfigurationProducer extends JavaRunConfigurationProducerBase<JavaTestConfigurationBase> {
  protected TestDiscoveryConfigurationProducer(ConfigurationType type) {
    super(type);
  }


  protected abstract void setPosition(JavaTestConfigurationBase configuration, PsiLocation<PsiMethod> position);
  protected abstract Pair<String, String> getPosition(JavaTestConfigurationBase configuration);

  protected void setupDiscoveryConfiguration(JavaTestConfigurationBase configuration, PsiMethod sourceMethod, Module targetModule) {
    setPosition(configuration, new PsiLocation<>(sourceMethod));
    Pair<String, String> position = getPosition(configuration);
    configuration.setName("Tests for " + StringUtil.getShortName(position.first) + "." + position.second);
    configuration.setModule(targetModule);
  }


  @Override
  protected boolean setupConfigurationFromContext(final JavaTestConfigurationBase configuration,
                                                  ConfigurationContext configurationContext,
                                                  Ref<PsiElement> ref) {
    if (!Registry.is(TestDiscoveryExtension.TEST_DISCOVERY_REGISTRY_KEY)) {
      return false;
    }
    final Location contextLocation = configurationContext.getLocation();
    assert contextLocation != null;
    final Location location = JavaExecutionUtil.stepIntoSingleClass(contextLocation);
    if (location == null) return false;
    final PsiMethod sourceMethod = getSourceMethod(location);
    final Pair<String, String> position = getPosition(sourceMethod);
    if (sourceMethod != null && position != null) {
      final Project project = configuration.getProject();
      final TestDiscoveryIndex testDiscoveryIndex = TestDiscoveryIndex.getInstance(project);
      if (testDiscoveryIndex.getTestsByMethodName(position.first, position.second, configuration.getTestFrameworkId()).isEmpty()) {
        return false;
      }

      Module targetModule = getTargetModule(configuration, configurationContext, position, project, testDiscoveryIndex);
      setupDiscoveryConfiguration(configuration, sourceMethod, targetModule);
      return true;
    }
    return false;
  }

  private Module getTargetModule(JavaTestConfigurationBase configuration,
                                 ConfigurationContext configurationContext,
                                 Pair<String, String> position, Project project, TestDiscoveryIndex testDiscoveryIndex) {
    final RunnerAndConfigurationSettings template =
      configurationContext.getRunManager().getConfigurationTemplate(getConfigurationFactory());
    final Module predefinedModule = ((ModuleBasedConfiguration)template.getConfiguration()).getConfigurationModule().getModule();
    if (predefinedModule != null) {
      return predefinedModule;
    }

    //potentially this set won't be big, it reflects modules from where user starts his tests
    final Collection<String> modules = testDiscoveryIndex.getTestModulesByMethodName(position.first,
                                                                                     position.second,
                                                                                     configuration.getTestFrameworkId());
    if (modules.isEmpty()) return null;

    final List<Module> survivedModules = new ArrayList<>();
    final ModuleManager moduleManager = ModuleManager.getInstance(project);
    for (String moduleName : modules) {
      final Module moduleByName = moduleManager.findModuleByName(moduleName);
      if (moduleByName != null) {
        survivedModules.add(moduleByName);
      }
    }
    if (survivedModules.isEmpty()) return null;

    return detectTargetModule(survivedModules, project);
  }

  public abstract boolean isApplicable(@NotNull PsiMethod method);

  @NotNull
  public abstract RunProfileState createProfile(@NotNull PsiMethod[] testMethods,
                                                Module module,
                                                RunConfiguration configuration,
                                                ExecutionEnvironment environment);

  public RunProfile createProfile(PsiMethod[] testMethods,
                                  Module module,
                                  ConfigurationContext context, 
                                  String configurationName) {
    RunnerAndConfigurationSettings settings = cloneTemplateConfiguration(context);
    JavaTestConfigurationBase configuration = (JavaTestConfigurationBase)settings.getConfiguration();
    configuration.setModule(module);
    if (module == null) {
      configuration.setSearchScope(TestSearchScope.WHOLE_PROJECT);
    }
    else {
      configuration.setSearchScope(TestSearchScope.MODULE_WITH_DEPENDENCIES);
    }
    configuration.setShortenCommandLine(ShortenCommandLine.MANIFEST);
    return new MyRunProfile(testMethods, module, configuration, configurationName);
  }

  public static Module detectTargetModule(Collection<Module> survivedModules, Project project) {
    ModuleManager moduleManager = ModuleManager.getInstance(project);
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
      return aModule;
    }
    return null;
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
    final String qualifiedName = ClassUtil.getJVMClassName(containingClass);
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

  private class MyRunProfile implements RunProfile, ConfigurationWithCommandLineShortener {
    private final PsiMethod[] myTestMethods;
    private final Module myModule;
    private final JavaTestConfigurationBase myConfiguration;
    private final String myConfigurationName;

    public MyRunProfile(PsiMethod[] testMethods, Module module, JavaTestConfigurationBase configuration, String configurationName) {
      myTestMethods = testMethods;
      myModule = module;
      myConfiguration = configuration;
      myConfigurationName = configurationName;
    }

    @Nullable
    @Override
    public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment) {
      return createProfile(myTestMethods, myModule, myConfiguration, environment);
    }

    @Override
    public String getName() {
      return myConfigurationName;
    }

    @Nullable
    @Override
    public Icon getIcon() {
      return myConfiguration.getIcon();
    }

    @Nullable
    @Override
    public ShortenCommandLine getShortenCommandLine() {
      return myConfiguration.getShortenCommandLine();
    }

    @Override
    public void setShortenCommandLine(ShortenCommandLine mode) {
      myConfiguration.setShortenCommandLine(mode);
    }

    @Override
    public Project getProject() {
      return myConfiguration.getProject();
    }
  }
}
