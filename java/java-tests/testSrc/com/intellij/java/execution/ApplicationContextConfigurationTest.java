// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.execution;

import com.intellij.execution.actions.CreateAction;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ExecutionDataKeys;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.MapDataContext;
import com.intellij.testFramework.TestActionEvent;
import org.jetbrains.annotations.NotNull;

public class ApplicationContextConfigurationTest extends BaseConfigurationTestCase {
  private static final String PACKAGE_NAME = "apackage";
  private static final String SHORT_CLASS_NAME = "SampleClass";
  private static final String CLASS_NAME = PACKAGE_NAME + "." + SHORT_CLASS_NAME;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    addModule("commonConfiguration");
  }

  public void testApplication() {
    PsiClass psiClass = findClass(getModule1(), CLASS_NAME);
    PsiMethod psiMethod = psiClass.findMethodsByName("main", false)[0];
    ApplicationConfiguration configuration = createConfiguration(psiMethod);
    assertEquals(CLASS_NAME, configuration.getMainClassName());
    assertEquals(configuration.suggestedName(), configuration.getName());
    assertEquals(SHORT_CLASS_NAME, configuration.getName());
  }

  public void testApplicationFromConsoleContext() {
    PsiClass psiClass = findClass(getModule1(), CLASS_NAME);
    PsiMethod psiMethod = psiClass.findMethodsByName("main", false)[0];
    ApplicationConfiguration configuration = createConfiguration(psiMethod);
    RunnerAndConfigurationSettingsImpl settings =
      new RunnerAndConfigurationSettingsImpl(RunManagerImpl.getInstanceImpl(myProject), configuration);
    ExecutionEnvironment e = ExecutionEnvironmentBuilder.createOrNull(DefaultRunExecutor.getRunExecutorInstance(), settings).build();
    MapDataContext dataContext = new MapDataContext();
    dataContext.put(ExecutionDataKeys.EXECUTION_ENVIRONMENT, e);
    AnActionEvent event = TestActionEvent.createTestEvent(dataContext);
    new CreateAction().update(event);
    assertTrue(event.getPresentation().isEnabledAndVisible());
  }

  @Override
  @NotNull
  protected String getTestDataPath() {
    return PathManagerEx.getTestDataPath();
  }
}
