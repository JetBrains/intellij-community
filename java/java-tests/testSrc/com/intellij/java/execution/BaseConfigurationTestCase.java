// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.execution;

import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.execution.testframework.AbstractJavaTestConfigurationProducer;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.project.IntelliJProjectConfiguration;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.*;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public abstract class BaseConfigurationTestCase extends IdeaTestCase {
  private final List<Module> myModulesToDispose = new ArrayList<>();

  @Override
  protected void tearDown() throws Exception {
    try {
      myModulesToDispose.clear();
    }
    finally {
      super.tearDown();
    }
  }

  protected void addModule(String path) {
    addModule(path, true);
  }

  protected void addModule(String path, boolean addSource) {
    VirtualFile module1Content = findFile(path);
    createModule(module1Content, addSource);
  }

  protected void createModule(VirtualFile module1Content, boolean addSource) {
    Module module = createEmptyModule();
    if (addSource) {
      PsiTestUtil.addSourceRoot(module, module1Content, true);
    }
    else {
      PsiTestUtil.addContentRoot(module, module1Content);
    }

    IntelliJProjectConfiguration.LibraryRoots junit4Library = IntelliJProjectConfiguration.getProjectLibrary("JUnit4");
    ModuleRootModificationUtil.addModuleLibrary(module, "JUnit4", junit4Library.getClassesUrls(), junit4Library.getSourcesUrls());
    ModuleRootModificationUtil.setModuleSdk(module, ModuleRootManager.getInstance(myModule).getSdk());
    GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module);
    assertNotNull(JavaPsiFacade.getInstance(getProject()).findClass(JUnitUtil.TEST_CASE_CLASS, scope));
    Module missingModule = createTempModule();
    addDependency(module, missingModule);
    ModuleManager.getInstance(myProject).disposeModule(missingModule);
  }

  protected Module createEmptyModule() {
    Module module = createTempModule();
    myModulesToDispose.add(module);
    return module;
  }

  private Module createTempModule() {
    return createTempModule(getTempDir(), myProject);
  }

  @NotNull
  public static Module createTempModule(TempFiles tempFiles, final Project project) {
    final String tempPath = tempFiles.createTempFile("xxx").getAbsolutePath();
    Module result = WriteAction.compute(() -> ModuleManager.getInstance(project).newModule(tempPath, StdModuleTypes.JAVA.getId()));
    PlatformTestUtil.saveProject(project);
    return result;
  }

  protected static VirtualFile findFile(String path) {
    String filePath = PathManagerEx.getTestDataPath() + File.separator + "junit" + File.separator + "configurations" +
                      File.separator + path;
    return LocalFileSystem.getInstance().findFileByPath(filePath.replace(File.separatorChar, '/'));
  }

  protected void disposeModule(Module module) {
    assertTrue(myModulesToDispose.remove(module));
    ModuleManager.getInstance(myProject).disposeModule(module);
  }

  protected Module getModule1() {
    return getModule(0);
  }

  protected Module getModule(int index) {
    return myModulesToDispose.get(index);
  }

  protected Module getModule2() {
    return getModule(1);
  }

  protected Module getModule4() {
    return getModule(3);
  }

  protected Module getModule3() {
    return getModule(2);
  }

  protected PsiClass findClass(Module module, String qualifiedName) {
    return findClass(qualifiedName, GlobalSearchScope.moduleScope(module));
  }

  protected PsiClass findClass(String qualifiedName, GlobalSearchScope scope) {
    return JavaPsiFacade.getInstance(myProject).findClass(qualifiedName, scope);
  }

  protected JUnitConfiguration createJUnitConfiguration(@NotNull PsiElement psiElement,
                                                        @NotNull Class<? extends AbstractJavaTestConfigurationProducer> producerClass,
                                                        @NotNull MapDataContext dataContext) {
    ConfigurationContext context = createContext(psiElement, dataContext);
    RunConfigurationProducer producer = RunConfigurationProducer.getInstance(producerClass);
    assert producer != null;
    ConfigurationFromContext fromContext = producer.createConfigurationFromContext(context);
    assertNotNull(fromContext);
    return (JUnitConfiguration)fromContext.getConfiguration();
  }

  protected final <T extends RunConfiguration> T createConfiguration(@NotNull PsiElement psiElement) {
    return createConfiguration(psiElement, new MapDataContext());
  }

  protected <T extends RunConfiguration> T createConfiguration(@NotNull PsiElement psiElement, @NotNull MapDataContext dataContext) {
    ConfigurationContext context = createContext(psiElement, dataContext);
    RunnerAndConfigurationSettings settings = context.getConfiguration();
    @SuppressWarnings("unchecked") T configuration = settings == null ? null : (T)settings.getConfiguration();
    return configuration;
  }

  public ConfigurationContext createContext(@NotNull PsiElement psiClass) {
    MapDataContext dataContext = new MapDataContext();
    return createContext(psiClass, dataContext);
  }

  public ConfigurationContext createContext(@NotNull PsiElement psiClass, @NotNull MapDataContext dataContext) {
    dataContext.put(CommonDataKeys.PROJECT, myProject);
    if (LangDataKeys.MODULE.getData(dataContext) == null) {
      dataContext.put(LangDataKeys.MODULE, ModuleUtilCore.findModuleForPsiElement(psiClass));
    }
    dataContext.put(Location.DATA_KEY, PsiLocation.fromPsiElement(psiClass));
    return ConfigurationContext.getFromContext(dataContext);
  }

  protected void addDependency(Module module, Module dependency) {
    ModuleRootModificationUtil.addDependency(module, dependency);
  }

  protected void checkPackage(String packageName, JUnitConfiguration configuration) {
    assertEquals(packageName, configuration.getPersistentData().getPackageName());
  }

  protected void checkClassName(String className, JUnitConfiguration configuration) {
    assertEquals(className, configuration.getPersistentData().getMainClassName());
  }

  protected void checkMethodName(String methodName, JUnitConfiguration configuration) {
    assertEquals(methodName, configuration.getPersistentData().getMethodName());
  }

  protected void checkTestObject(String testObjectKey, JUnitConfiguration configuration) {
    assertEquals(testObjectKey, configuration.getPersistentData().TEST_OBJECT);
  }
}
