// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.execution;

import com.intellij.execution.RunManager;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImplicitClass;
import com.intellij.psi.util.JavaImplicitClassUtil;
import com.intellij.testFramework.MapDataContext;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

public class ApplicationConfigurationImplicitClassTest extends BaseConfigurationTestCase {
  @Language("JAVA") private static final String APPLICATION_CODE =
      """
      public static void main(String[] args) {
      }
      """;
  private TestSources mySource;

  public void testImplicitApplication() {
    String implicitClassName = "Test1";
    PsiFile file = mySource.createFile(implicitClassName + ".java", APPLICATION_CODE + System.lineSeparator());
    PsiImplicitClass psiImplicitClass = JavaImplicitClassUtil.getImplicitClassFor(file);
    ApplicationConfiguration configuration = createConfiguration(psiImplicitClass);
    assertNotNull(configuration);
    try {
      configuration.checkConfiguration();
    }
    catch (RuntimeConfigurationException e) {
      fail("Unexpected ConfigurationException: " + e);
    }
    assertEquals(implicitClassName, configuration.getMainClassName());
    assertEquals(implicitClassName, configuration.getRunClass());
    assertTrue(configuration.isImplicitClassConfiguration());
  }

  private void initModule() {
    mySource.initModule();
    mySource.copyJdkFrom(myModule);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    mySource = new TestSources(myProject, getTempDir());
    initModule();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      mySource.tearDown();
      mySource = null;
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  @Override
  protected <T extends RunConfiguration> T createConfiguration(@NotNull PsiElement psiClass, @NotNull MapDataContext dataContext) {
    T configuration = super.createConfiguration(psiClass, dataContext);
    RunManagerImpl manager = (RunManagerImpl)RunManager.getInstance(myProject);
    manager.setTemporaryConfiguration(new RunnerAndConfigurationSettingsImpl(manager, configuration, false));
    return configuration;
  }
}
