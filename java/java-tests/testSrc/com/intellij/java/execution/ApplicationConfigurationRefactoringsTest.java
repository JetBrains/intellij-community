// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.execution;

import com.intellij.execution.RunManager;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesProcessor;
import com.intellij.refactoring.move.moveClassesOrPackages.SingleSourceRootMoveDestination;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.testFramework.MapDataContext;
import org.jetbrains.annotations.NotNull;

public class ApplicationConfigurationRefactoringsTest extends BaseConfigurationTestCase {
  private static final String APPLICATION_CODE = "public class Application {" +
                                                 "  public static void main(String[] args) {\n" +
                                                 "  }" +
                                                 "}";
  private TestSources mySource;

  public void testRenameApplication() {
    PsiClass psiClass = mySource.createClass("Application", APPLICATION_CODE);
    assertNotNull(psiClass);
    ApplicationConfiguration configuration = createConfiguration(psiClass);
    assertNotNull(configuration);
    rename(psiClass, "NewName");
    try {
      configuration.checkConfiguration();
    }
    catch (RuntimeConfigurationException e) {
      fail("Unexpected ConfigurationException: " + e);
    }
    assertEquals("NewName", configuration.getMainClassName());
  }

  public void testMoveApplication() {
    PsiClass psiClass = mySource.createClass("Application", APPLICATION_CODE);
    assertNotNull(psiClass);
    ApplicationConfiguration configuration = createConfiguration(psiClass);
    move(psiClass, "pkg");
    try {
      configuration.checkConfiguration();
    }
    catch (RuntimeConfigurationException e) {
      fail("Unexpected ConfigurationException: " + e);
    }

    assertEquals("pkg.Application", configuration.getMainClassName());
    rename(JavaPsiFacade.getInstance(myProject).findPackage("pkg"), "pkg2");
    assertEquals("pkg2.Application", configuration.getMainClassName());
  }

  private void initModule() {
    mySource.initModule();
    mySource.copyJdkFrom(myModule);
  }

  private void move(final PsiElement psiElement, String packageName) {
    VirtualFile pkgFile = mySource.createPackageDir(packageName);
    final PsiDirectory toDir = PsiManager.getInstance(myProject).findDirectory(pkgFile);
    assertNotNull(toDir);
    PackageWrapper wrapper = PackageWrapper.create(JavaDirectoryService.getInstance().getPackage(toDir));
    new MoveClassesOrPackagesProcessor(myProject, new PsiElement[]{psiElement},
                                       new SingleSourceRootMoveDestination(wrapper, toDir),
                                       false, false, null).run();
  }

  private void rename(final PsiElement psiElement, final String newName) {
    new RenameProcessor(myProject, psiElement, newName, false, false).run();
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
