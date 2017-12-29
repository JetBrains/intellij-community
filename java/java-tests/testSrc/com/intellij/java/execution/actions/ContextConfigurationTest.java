/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.java.execution.actions;

import com.intellij.execution.Location;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.junit.*;
import com.intellij.execution.junit2.PsiMemberParameterizedLocation;
import com.intellij.execution.junit2.info.MethodLocation;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.java.execution.BaseConfigurationTestCase;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPackage;
import com.intellij.testFramework.MapDataContext;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

public class ContextConfigurationTest extends BaseConfigurationTestCase {
  private static final String PACKAGE_NAME = "apackage";
  private static final String SHORT_CLASS_NAME = "SampleClass";
  private static final String CLASS_NAME = PACKAGE_NAME + "." + SHORT_CLASS_NAME;
  private static final String METHOD_NAME = "test1";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    addModule("commonConfiguration");
  }

  public void testAbstractJUnit3TestCase() {
    String packageName = "abstractTests";
    String shortName = "AbstractTest";
    String qualifiedName = StringUtil.getQualifiedName(packageName, shortName);
    PsiClass psiClass = findClass(getModule1(), qualifiedName);
    PsiMethod testMethod = psiClass.findMethodsByName(METHOD_NAME, false)[0];
    JUnitConfiguration configuration = createConfiguration(testMethod);

    checkClassName(qualifiedName, configuration);
    checkMethodName(METHOD_NAME, configuration);
    checkPackage(packageName, configuration);
    checkGeneretedName(configuration, shortName + "." + METHOD_NAME);
  }

  public void testMethodInAbstractJUnit3TestCase() {
    String packageName = "abstractTests";
    String shortName = "AbstractTestImpl1";
    String qualifiedName = StringUtil.getQualifiedName(packageName, shortName);
    PsiClass psiClass = findClass(getModule1(), qualifiedName);
    PsiMethod testMethod = psiClass.findMethodsByName(METHOD_NAME, true)[0];

    MapDataContext dataContext = new MapDataContext();
    dataContext.put(CommonDataKeys.PROJECT, myProject);
    if (LangDataKeys.MODULE.getData(dataContext) == null) {
      dataContext.put(LangDataKeys.MODULE, ModuleUtilCore.findModuleForPsiElement(testMethod));
    }
    dataContext.put(Location.DATA_KEY, MethodLocation.elementInClass(testMethod, psiClass));

    ConfigurationContext context = ConfigurationContext.getFromContext(dataContext);
    RunnerAndConfigurationSettings settings = context.getConfiguration();
    JUnitConfiguration configuration = (JUnitConfiguration)settings.getConfiguration();

    checkClassName(qualifiedName, configuration);
    checkMethodName(METHOD_NAME, configuration);
    checkPackage(packageName, configuration);
    checkGeneretedName(configuration, shortName + "." + METHOD_NAME);
  }
  
  //fake parameterized by providing corresponding location
  public void testMethodInAbstractParameterizedTest() {
    String packageName = "abstractTests";
    String shortName = "AbstractTestImpl1";
    String qualifiedName = StringUtil.getQualifiedName(packageName, shortName);
    PsiClass psiClass = findClass(getModule1(), qualifiedName);
    PsiMethod testMethod = psiClass.findMethodsByName(METHOD_NAME, true)[0];

    MapDataContext dataContext = new MapDataContext();
    dataContext.put(CommonDataKeys.PROJECT, myProject);
    if (LangDataKeys.MODULE.getData(dataContext) == null) {
      dataContext.put(LangDataKeys.MODULE, ModuleUtilCore.findModuleForPsiElement(testMethod));
    }
    dataContext.put(Location.DATA_KEY, new PsiMemberParameterizedLocation(myProject, testMethod, psiClass, "param"));

    ConfigurationContext context = ConfigurationContext.getFromContext(dataContext);
    RunnerAndConfigurationSettings settings = context.getConfiguration();
    JUnitConfiguration configuration = (JUnitConfiguration)settings.getConfiguration();

    checkClassName(qualifiedName, configuration);
    checkMethodName(METHOD_NAME, configuration);
    checkPackage(packageName, configuration);
    checkGeneretedName(configuration, shortName + "." + METHOD_NAME);
  }

  public void testJUnitMethodTest() {
    PsiClass psiClass = findClass(getModule1(), CLASS_NAME);
    PsiMethod testMethod = psiClass.findMethodsByName(METHOD_NAME, false)[0];
    JUnitConfiguration configuration = createConfiguration(testMethod);
    checkTestObject(JUnitConfiguration.TEST_METHOD, configuration);
    checkClassName(CLASS_NAME, configuration);
    checkMethodName(METHOD_NAME, configuration);
    checkPackage(PACKAGE_NAME, configuration);
    checkGeneretedName(configuration, SHORT_CLASS_NAME + "." + METHOD_NAME);
  }

  public void testJUnitClassTest() {
    PsiClass psiClass = findClass(getModule1(), CLASS_NAME);
    final MapDataContext dataContext = new MapDataContext();
    JUnitConfiguration configuration = createJUnitConfiguration(psiClass, TestInClassConfigurationProducer.class, dataContext);
    checkTestObject(JUnitConfiguration.TEST_CLASS, configuration);
    checkClassName(CLASS_NAME, configuration);
    checkPackage(PACKAGE_NAME, configuration);
    checkGeneretedName(configuration, SHORT_CLASS_NAME);
  }


  public void testRecreateJUnitClass() {
    createEmptyModule();
    addDependency(getModule2(), getModule1());
    PsiClass psiClass = findClass(getModule1(), CLASS_NAME);
    PsiPackage psiPackage = JUnitUtil.getContainingPackage(psiClass);
    JUnitConfiguration configuration = createJUnitConfiguration(psiPackage, AllInPackageConfigurationProducer.class, new MapDataContext());
    configuration.getPersistentData().setScope(TestSearchScope.MODULE_WITH_DEPENDENCIES);
    configuration.setModule(getModule2());
    MapDataContext dataContext = new MapDataContext();
    dataContext.put(DataConstantsEx.RUNTIME_CONFIGURATION, configuration);
    configuration = createJUnitConfiguration(psiClass, TestInClassConfigurationProducer.class, dataContext);
    checkClassName(psiClass.getQualifiedName(), configuration);
    assertEquals(Collections.singleton(getModule2()), new HashSet(Arrays.asList(configuration.getModules())));
  }

  public void testJUnitPackage() {
    PsiClass psiClass = findClass(getModule1(), CLASS_NAME);
    PsiPackage psiPackage = JUnitUtil.getContainingPackage(psiClass);
    final MapDataContext dataContext = new MapDataContext();
    final Module module = ModuleUtilCore.findModuleForPsiElement(psiClass);
    dataContext.put(DataConstants.MODULE, module);
    JUnitConfiguration configuration = createJUnitConfiguration(psiPackage, AllInPackageConfigurationProducer.class, dataContext);
    checkTestObject(JUnitConfiguration.TEST_PACKAGE, configuration);
    checkPackage(PACKAGE_NAME, configuration);
    checkGeneretedName(configuration, PACKAGE_NAME + " in " + module.getName());
  }

  public void testJUnitDefaultPackage() {
    PsiClass psiClass = findClass(getModule1(), CLASS_NAME);
    PsiPackage psiPackage = JUnitUtil.getContainingPackage(psiClass);
    PsiPackage defaultPackage = psiPackage.getParentPackage();
    final Module module = ModuleUtilCore.findModuleForPsiElement(psiClass);
    final MapDataContext dataContext = new MapDataContext();
    dataContext.put(DataConstants.MODULE, module);
    JUnitConfiguration configuration = createJUnitConfiguration(defaultPackage, AllInPackageConfigurationProducer.class, dataContext);
    checkTestObject(JUnitConfiguration.TEST_PACKAGE, configuration);
    checkPackage("", configuration);
    checkGeneretedName(configuration, "All in " + module.getName());
  }

  public void testApplication() {
    PsiClass psiClass = findClass(getModule1(), CLASS_NAME);
    PsiMethod psiMethod = psiClass.findMethodsByName("main", false)[0];
    ApplicationConfiguration configuration = createConfiguration(psiMethod);
    assertEquals(CLASS_NAME, configuration.getMainClassName());
    assertEquals(configuration.suggestedName(), configuration.getName());
    assertEquals(SHORT_CLASS_NAME, configuration.getName());
  }

  public void testReusingConfiguration() {
    RunManager runManager = RunManager.getInstance(myProject);
    PsiClass psiClass = findClass(getModule1(), CLASS_NAME);
    PsiPackage psiPackage = JUnitUtil.getContainingPackage(psiClass);

    ConfigurationContext context = createContext(psiClass);
    assertNull(context.findExisting());
    RunnerAndConfigurationSettings testClass = context.getConfiguration();
    runManager.addConfiguration(testClass,  false);
    context = createContext(psiClass);
    assertSame(testClass, context.findExisting());

    runManager.setSelectedConfiguration(testClass);
    context = createContext(psiPackage);
    assertNull(context.findExisting());
    RunnerAndConfigurationSettings testPackage = context.getConfiguration();
    runManager.addConfiguration(testPackage,  false);
    context = createContext(psiPackage);
    assertSame(testPackage, context.findExisting());
    assertSame(testClass, runManager.getSelectedConfiguration());
    runManager.setSelectedConfiguration(context.findExisting());
    assertSame(testPackage, runManager.getSelectedConfiguration());
  }

  public void testJUnitGeneratedName() {
    PsiClass psiClass = findClass(getModule1(), CLASS_NAME);
    PsiPackage psiPackage = JUnitUtil.getContainingPackage(psiClass);
    JUnitConfiguration configuration = new JUnitConfiguration(null, myProject, JUnitConfigurationType.getInstance().getConfigurationFactories()[0]);
    JUnitConfiguration.Data data = configuration.getPersistentData();
    data.PACKAGE_NAME = psiPackage.getQualifiedName();
    data.TEST_OBJECT = JUnitConfiguration.TEST_PACKAGE;
    assertEquals(PACKAGE_NAME, configuration.suggestedName());
    data.PACKAGE_NAME = "not.existing.pkg";
    assertEquals("not.existing.pkg", configuration.suggestedName());

    data.TEST_OBJECT = JUnitConfiguration.TEST_CLASS;
    data.MAIN_CLASS_NAME = psiClass.getQualifiedName();
    assertEquals(SHORT_CLASS_NAME, configuration.suggestedName());
    data.MAIN_CLASS_NAME = "not.existing.TestClass";
    assertEquals("TestClass", configuration.suggestedName());
    data.MAIN_CLASS_NAME = "pkg.TestClass.";
    assertEquals("pkg.TestClass.", configuration.suggestedName());
    data.MAIN_CLASS_NAME = "TestClass";
    assertEquals("TestClass", configuration.suggestedName());

    data.TEST_OBJECT = JUnitConfiguration.TEST_METHOD;
    data.MAIN_CLASS_NAME = psiClass.getQualifiedName();
    data.METHOD_NAME = METHOD_NAME;
    assertEquals(SHORT_CLASS_NAME + "." + METHOD_NAME, configuration.suggestedName());
    data.MAIN_CLASS_NAME = "not.existing.TestClass";
    assertEquals("TestClass." + METHOD_NAME, configuration.suggestedName());
  }

  private static void checkGeneretedName(JUnitConfiguration configuration, String name) {
    assertEquals(configuration.suggestedName(), configuration.getName());
    assertEquals(name, configuration.getName());
  }
}
