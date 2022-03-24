// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.JavaRunConfigurationModule;
import com.intellij.execution.configurations.RefactoringListenerProvider;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.execution.testframework.sm.runner.SMRunnerConsolePropertiesProvider;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class JavaTestConfigurationBase extends JavaRunConfigurationBase
  implements RefactoringListenerProvider, SMRunnerConsolePropertiesProvider {

  private ShortenCommandLine myShortenCommandLine = null;
  private boolean myUseModulePath = true;
  private static final @NonNls String USE_CLASS_PATH_ONLY = "useClassPathOnly";

  public JavaTestConfigurationBase(String name,
                                   @NotNull JavaRunConfigurationModule configurationModule,
                                   @NotNull ConfigurationFactory factory) {
    super(name, configurationModule, factory);
  }

  public JavaTestConfigurationBase(@NotNull JavaRunConfigurationModule configurationModule, @NotNull ConfigurationFactory factory) {
    super(configurationModule, factory);
  }

  public abstract void bePatternConfiguration(List<PsiClass> classes, PsiMethod method);

  public abstract void beMethodConfiguration(Location<PsiMethod> location);

  public abstract void beClassConfiguration(PsiClass aClass);

  public void withNestedClass(PsiClass aClass) {}

  public abstract boolean isConfiguredByElement(PsiElement element);

  public abstract @NonNls String getTestType();

  public String prepareParameterizedParameter(String paramSetName) {
    return paramSetName;
  }

  public abstract TestSearchScope getTestSearchScope();
  public abstract void setSearchScope(TestSearchScope searchScope);

  @Nullable
  @Override
  public abstract JavaTestFrameworkRunnableState<? extends JavaTestConfigurationBase> getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment) throws ExecutionException;

  @Nullable
  @Override
  public ShortenCommandLine getShortenCommandLine() {
    return myShortenCommandLine;
  }

  @Override
  public void setShortenCommandLine(@Nullable ShortenCommandLine shortenCommandLine) {
    myShortenCommandLine = shortenCommandLine;
  }

  @Override
  public void readExternal(@NotNull Element element) throws InvalidDataException {
    super.readExternal(element);
    setShortenCommandLine(ShortenCommandLine.readShortenClasspathMethod(element));
    myUseModulePath = element.getChild(USE_CLASS_PATH_ONLY) == null;
  }

  @Override
  public void writeExternal(@NotNull Element element) throws WriteExternalException {
    super.writeExternal(element);
    ShortenCommandLine.writeShortenClasspathMethod(element, myShortenCommandLine);
    if (!myUseModulePath) {
      element.addContent(new Element(USE_CLASS_PATH_ONLY));
    }
  }

  public boolean isUseModulePath() {
    return myUseModulePath;
  }

  public void setUseModulePath(boolean useModulePath) {
    myUseModulePath = useModulePath;
  }
}
