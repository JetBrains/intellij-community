// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.execution;

import com.intellij.execution.RunManager;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.execution.junit.AllInPackageConfigurationProducer;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.testframework.AbstractJavaTestConfigurationProducer;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.project.IntelliJProjectConfiguration;
import com.intellij.psi.*;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesProcessor;
import com.intellij.refactoring.move.moveClassesOrPackages.SingleSourceRootMoveDestination;
import com.intellij.refactoring.move.moveMembers.MockMoveMembersOptions;
import com.intellij.refactoring.move.moveMembers.MoveMembersProcessor;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.testFramework.MapDataContext;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.HashSet;

public class ConfigurationRefactoringsTest extends BaseConfigurationTestCase {
  private static final String APPLICATION_CODE = "public class Application {" +
                                                 "  public static void main(String[] args) {\n" +
                                                 "  }" +
                                                 "}";
  private static final String TEST_CODE = "import junit.framework.TestCase;" +
                                          "public class ATest extends TestCase {" +
                                          "public void test() {}" +
                                          "private void otherMethod() {}" +
                                          "}";
  private TestSources mySource;
  private static final String NOT_A_TEST = "public class NotATest {" +
                                           "public void test() {}" +
                                           "}";
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

  public void testRenameJUnitPackage() {
    PsiPackage psiPackage = mySource.createPackage("pkg");
    JUnitConfiguration configuration = createJUnitConfiguration(psiPackage, AllInPackageConfigurationProducer.class, new MapDataContext());
    rename(psiPackage, "pkg2");
    checkPackage("pkg2", configuration);
    PsiPackage outer = mySource.createPackage("outerPkg");
    move(JavaPsiFacade.getInstance(myProject).findPackage("pkg2"), outer.getQualifiedName());
    checkPackage("outerPkg.pkg2", configuration);
    rename(outer, "outer2");
    checkPackage("outer2.pkg2", configuration);
  }

  public void testRenameJUnitContainingPackage() {
    PsiClass psiClass = mySource.createClass("ATest", TEST_CODE);
    assertNotNull(psiClass);
    JUnitConfiguration configuration = createConfiguration(psiClass);
    PsiPackage psiPackage = mySource.createPackage("pkg");
    move(psiClass, "pkg");
    checkClassName("pkg.ATest", configuration);
    rename(psiPackage, "newPkg");
    checkClassName("newPkg.ATest", configuration);
    psiPackage = mySource.findPackage("newPkg");

    mySource.createPackage("pkg2");
    move(psiPackage, "pkg2");
    checkClassName("pkg2.newPkg.ATest", configuration);
  }

  public void testRefactorTestMethod() {
    PsiClass psiClass = mySource.createClass("ATest", TEST_CODE);
    assertNotNull(psiClass);
    PsiMethod testMethod = psiClass.findMethodsByName("test", false)[0];
    JUnitConfiguration configuration = createConfiguration(testMethod);
    rename(testMethod, "test1");
    checkMethodName("test1", configuration);
    checkClassName("ATest", configuration);
    assertEquals("ATest.test1", configuration.getName());
    move(psiClass, "pkg");
    checkClassName("pkg.ATest", configuration);
    psiClass = configuration.getConfigurationModule().findClass(configuration.getPersistentData().getMainClassName());
    rename(psiClass, "TestClassName");
    assertEquals("TestClassName.test1", configuration.getName());
    psiClass = configuration.getConfigurationModule().findClass(configuration.getPersistentData().getMainClassName());

    PsiClass otherTest = mySource.createClass("ATest", TEST_CODE);
    HashSet<PsiMember> members = new HashSet<>();
    assertNotNull(psiClass);
    members.add(psiClass.findMethodsByName("test1", false)[0]);
    moveMembers(otherTest, members);
    psiClass = configuration.getConfigurationModule().findClass(configuration.getPersistentData().getMainClassName());
    checkMethodName("test1", configuration);
    checkClassName("ATest", configuration);
    assertEquals("ATest.test1", configuration.getName());

    assertNotNull(psiClass);
    PsiMethod otherMethod = psiClass.findMethodsByName("otherMethod", false)[0];
    rename(otherMethod, "newName");
    checkMethodName("test1", configuration);
  }

  public void testRenameBadTestClass() {
    PsiClass psiClass = mySource.createClass("NotATest", NOT_A_TEST);
    assertNotNull(psiClass);
    JUnitConfiguration configuration = new JUnitConfiguration("notATest", myProject);
    configuration.setMainClass(psiClass);
    configuration.setModule(configuration.getValidModules().iterator().next());

    checkConfigurationException("Class 'NotATest' is not a test", configuration);

    RunManagerImpl runManager = (RunManagerImpl)RunManager.getInstance(myProject);
    runManager.setTemporaryConfiguration(new RunnerAndConfigurationSettingsImpl(runManager, configuration, false));
    rename(psiClass, "NotATest2");
    JUnitConfiguration.Data data = configuration.getPersistentData();
    assertEquals("NotATest2", data.getMainClassName());

    data.METHOD_NAME = "test";
    data.TEST_OBJECT = JUnitConfiguration.TEST_METHOD;
    checkConfigurationException("Class 'NotATest2' is not a test", configuration);
    rename(psiClass.findMethodsByName("test", false)[0], "test2");
    assertEquals("NotATest2", data.getMainClassName());
    assertEquals("test2", data.getMethodName());
  }

  private static void checkConfigurationException(String expectedExceptionMessage, JUnitConfiguration configuration) {
    try {
      configuration.checkConfiguration();
    }
    catch (RuntimeConfigurationException e) {
      assertEquals(expectedExceptionMessage, e.getLocalizedMessage());
      return;
    }
    fail("ConfigurationException expected");
  }

  public void testRefactorOtherClass() {
    PsiClass psiClass = mySource.createClass("ATest", TEST_CODE);
    assertNotNull(psiClass);
    JUnitConfiguration configuration = createConfiguration(psiClass);

    psiClass = mySource.createClass("Application", APPLICATION_CODE);
    assertNotNull(psiClass);
    rename(psiClass, "NewName");
    checkClassName("ATest", configuration);
    mySource.createPackage("pkg");

    psiClass = mySource.findClass("NewName");
    assertNotNull(psiClass);
    move(psiClass, "pkg");
    checkClassName("ATest", configuration);
  }

  private void moveMembers(final PsiClass otherTest, final HashSet<PsiMember> members) {
    MockMoveMembersOptions options = new MockMoveMembersOptions(otherTest.getQualifiedName(), members);
    new MoveMembersProcessor(myProject, null, options).run();
  }

  private void initModule() {
    mySource.initModule();
    mySource.copyJdkFrom(myModule);
    IntelliJProjectConfiguration.LibraryRoots junit4Library = IntelliJProjectConfiguration.getProjectLibrary("JUnit4");
    for (File file : junit4Library.getClasses()) {
      VirtualFile libFile = LocalFileSystem.getInstance().findFileByIoFile(file);
      assertNotNull(libFile);
      mySource.addLibrary(JarFileSystem.getInstance().getJarRootForLocalFile(libFile));
    }
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

  @Override
  protected JUnitConfiguration createJUnitConfiguration(@NotNull PsiElement psiElement,
                                                        @NotNull Class<? extends AbstractJavaTestConfigurationProducer<?>> producerClass,
                                                        @NotNull MapDataContext dataContext) {
    final JUnitConfiguration configuration = super.createJUnitConfiguration(psiElement, producerClass, dataContext);
    RunManagerImpl manager = (RunManagerImpl)RunManager.getInstance(myProject);
    manager.setTemporaryConfiguration(new RunnerAndConfigurationSettingsImpl(manager, configuration));
    return configuration;
  }
}
