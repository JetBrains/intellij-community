// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.execution;

import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.execution.testframework.AbstractJavaTestConfigurationProducer;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
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
import com.theoryinpractice.testng.configuration.TestNGConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class BaseConfigurationTestCase extends JavaProjectTestCase {
  private final List<Module> myModulesToDispose = new ArrayList<>();

  @Override
  protected void tearDown() throws Exception {
    try {
      myModulesToDispose.clear();
    }
    catch (Throwable e) {
      addSuppressedException(e);
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
    createModule(module1Content, addSource, "JUnit4");
  }

  protected void createModule(VirtualFile module1Content,
                              boolean addSource,
                              String junitLibName) {
    Module module = createEmptyModule();
    if (addSource) {
      PsiTestUtil.addSourceRoot(module, module1Content, true);
    }
    else {
      PsiTestUtil.addContentRoot(module, module1Content);
    }

    IntelliJProjectConfiguration.LibraryRoots junit4Library = IntelliJProjectConfiguration.getProjectLibrary(junitLibName);
    ModuleRootModificationUtil.addModuleLibrary(module, junitLibName, junit4Library.getClassesUrls(), junit4Library.getSourcesUrls());
    ModuleRootModificationUtil.setModuleSdk(module, ModuleRootManager.getInstance(myModule).getSdk());
    GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module);
    if ("JUnit4".equals(junitLibName)) {
      IndexingTestUtil.waitUntilIndexesAreReady(getProject());
      assertNotNull(JavaPsiFacade.getInstance(getProject()).findClass(JUnitUtil.TEST_CASE_CLASS, scope));
    }
    Module missingModule = createTempModule();
    addDependency(module, missingModule);
    ModuleManager.getInstance(myProject).disposeModule(missingModule);
    IndexingTestUtil.waitUntilIndexesAreReady(getProject());
  }

  protected @NotNull Module createEmptyModule() {
    Module module = createTempModule();
    myModulesToDispose.add(module);
    return module;
  }

  private @NotNull Module createTempModule() {
    return createTempModule(getTempDir(), myProject);
  }

  public static @NotNull Module createTempModule(@NotNull TemporaryDirectory tempDir, Project project) {
    Path tempPath = tempDir.newPath(".iml");
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    Module result = WriteAction.compute(() -> moduleManager.newModule(tempPath, JavaModuleType.getModuleType().getId()));
    PlatformTestUtil.saveProject(project);
    IndexingTestUtil.waitUntilIndexesAreReady(project);
    return result;
  }

  protected @NotNull String getTestDataPath() {
    return "";
  };

  protected VirtualFile findFile(String path) {
    String filePath = getTestDataPath() + File.separatorChar + "configuration" + File.separatorChar + path;
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

  protected PsiClass findClass(@NotNull Module module, String qualifiedName) {
    return findClass(qualifiedName, GlobalSearchScope.moduleScope(module));
  }

  protected PsiClass findClass(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    return JavaPsiFacade.getInstance(myProject).findClass(qualifiedName, scope);
  }

  protected TestNGConfiguration createTestNGConfiguration(@NotNull PsiElement psiElement,
                                                          @NotNull Class<? extends AbstractJavaTestConfigurationProducer<?>> producerClass,
                                                          @NotNull MapDataContext dataContext) {
    ConfigurationContext context = createContext(psiElement, dataContext);
    RunConfigurationProducer<?> producer = RunConfigurationProducer.getInstance(producerClass);
    ConfigurationFromContext fromContext = producer.createConfigurationFromContext(context);
    assertThat(fromContext).isNotNull();
    return (TestNGConfiguration)fromContext.getConfiguration();
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
    if (PlatformCoreDataKeys.MODULE.getData(dataContext) == null) {
      dataContext.put(PlatformCoreDataKeys.MODULE, ModuleUtilCore.findModuleForPsiElement(psiClass));
    }
    dataContext.put(Location.DATA_KEY, PsiLocation.fromPsiElement(psiClass));
    return ConfigurationContext.getFromContext(dataContext, ActionPlaces.UNKNOWN);
  }

  protected void addDependency(Module module, Module dependency) {
    ModuleRootModificationUtil.addDependency(module, dependency);
  }
}