// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.execution;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.PathsList;

import java.io.File;

public class ApplicationModulePathTest extends BaseConfigurationTestCase {


  public void testAdditionalModules() throws Exception {
    ApplicationConfiguration configuration = setupConfiguration(getTestName(true), myModule);
    configuration.setVMParameters("--add-modules java.se,java.xml.bind");
    ExecutionEnvironment environment =
      ExecutionEnvironmentBuilder.create(myProject, DefaultRunExecutor.getRunExecutorInstance(), configuration).build();
    JavaParameters params4Tests = 
      new ApplicationConfiguration.JavaApplicationCommandLineState<>(configuration, environment).createJavaParameters4Test();
    
    PathsList modulePath = params4Tests.getModulePath();
    assertTrue("module path: " + modulePath.getPathsString(),
               modulePath.getPathList().contains(getCompilerOutputPath(myModule)));
    //this module and se & xml.bind
    assertSize(3, modulePath.getPathList());
  }

  public void testServices() throws ExecutionException {
    Module module2 = createEmptyModule();
    setupModule(getTestName(true) + "/dep", module2, getTestRootDisposable());
    
    Module module3 = createEmptyModule();
    setupModule(getTestName(true) + "/dep1", module3, getTestRootDisposable());

    ApplicationConfiguration configuration = setupConfiguration(getTestName(true), myModule);
    ModuleRootModificationUtil.updateModel(myModule, model -> model.addModuleOrderEntry(module2));
    ModuleRootModificationUtil.updateModel(module2, model -> model.addModuleOrderEntry(module3));
    
    ExecutionEnvironment environment =
      ExecutionEnvironmentBuilder.create(myProject, DefaultRunExecutor.getRunExecutorInstance(), configuration).build();
    JavaParameters params4Tests = 
      new ApplicationConfiguration.JavaApplicationCommandLineState<>(configuration, environment).createJavaParameters4Test();
    
    PathsList modulePath = params4Tests.getModulePath();
    assertSize(3, modulePath.getPathList());
  }

  private ApplicationConfiguration setupConfiguration(String sources, Module module) {
    setupModule(sources, module, getTestRootDisposable());

    
    PsiClass aClass = findClass(module, "p.Main");
    assertNotNull(aClass);

    return createConfiguration(aClass);
  }

  private static void setupModule(String sources, Module module, Disposable parentDisposable) {
    VirtualFile contentRoot = getContentRoot(sources);
    ModuleRootModificationUtil.updateModel(module, model -> {
      ContentEntry contentEntry = model.addContentEntry(contentRoot);
      contentEntry.addSourceFolder(contentRoot.getUrl() + "/src", false);

      CompilerModuleExtension moduleExtension = model.getModuleExtension(CompilerModuleExtension.class);
      moduleExtension.inheritCompilerOutputPath(false);
      moduleExtension.setCompilerOutputPath(contentRoot.findFileByRelativePath("out/production"));
    });
    Sdk jdk9 = IdeaTestUtil.getMockJdk9();
    WriteAction.runAndWait(()-> ProjectJdkTable.getInstance().addJdk(jdk9, parentDisposable));
    ModuleRootModificationUtil.setModuleSdk(module, jdk9);
  }

  protected static VirtualFile getContentRoot(String path) {
    String filePath = PathManagerEx.getTestDataPath() + File.separator + "application" + File.separator + "modulePath" +
                      File.separator + path;
    return LocalFileSystem.getInstance().findFileByPath(filePath.replace(File.separatorChar, '/'));
  }

  private static String getCompilerOutputPath(Module module) {
    return PathUtil.getLocalPath(CompilerModuleExtension.getInstance(module).getCompilerOutputPath());
  }
}
