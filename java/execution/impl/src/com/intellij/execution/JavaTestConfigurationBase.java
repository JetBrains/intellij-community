/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.execution;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.JavaRunConfigurationModule;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RefactoringListenerProvider;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.execution.testframework.sm.runner.SMRunnerConsolePropertiesProvider;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class JavaTestConfigurationBase extends ModuleBasedConfiguration<JavaRunConfigurationModule>
  implements CommonJavaRunConfigurationParameters, ConfigurationWithCommandLineShortener, RefactoringListenerProvider, SMRunnerConsolePropertiesProvider {
  private ShortenCommandLine myShortenCommandLine = null;

  public JavaTestConfigurationBase(String name,
                                   @NotNull JavaRunConfigurationModule configurationModule,
                                   @NotNull ConfigurationFactory factory) {
    super(name, configurationModule, factory);
  }

  public JavaTestConfigurationBase(JavaRunConfigurationModule configurationModule,
                                   ConfigurationFactory factory) {
    super(configurationModule, factory);
  }

  @NotNull
  public abstract String getFrameworkPrefix();

  public abstract void bePatternConfiguration(List<PsiClass> classes, PsiMethod method);

  public abstract void beMethodConfiguration(Location<PsiMethod> location);

  public abstract void beClassConfiguration(PsiClass aClass);

  public abstract boolean isConfiguredByElement(PsiElement element);

  public String prepareParameterizedParameter(String paramSetName) {
    return paramSetName;
  }

  public abstract TestSearchScope getTestSearchScope();

  @Nullable
  @Override
  public ShortenCommandLine getShortenCommandLine() {
    return myShortenCommandLine;
  }

  @Override
  public void setShortenCommandLine(ShortenCommandLine shortenCommandLine) {
    myShortenCommandLine = shortenCommandLine;
  }

  @Override
  public void readExternal(@NotNull Element element) throws InvalidDataException {
    super.readExternal(element);
    setShortenCommandLine(ShortenCommandLine.readShortenClasspathMethod(element));
  }

  @Override
  public void writeExternal(@NotNull Element element) throws WriteExternalException {
    super.writeExternal(element);
    ShortenCommandLine.writeShortenClasspathMethod(element, myShortenCommandLine);
  }
}
