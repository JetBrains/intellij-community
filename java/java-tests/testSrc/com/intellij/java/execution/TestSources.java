// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.execution;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.TemporaryDirectory;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class TestSources {
  private final Project myProject;
  private final TemporaryDirectory myTempFiles;
  private Path mySrc;
  private Module myModule;

  public TestSources(@NotNull Project project, @NotNull TemporaryDirectory temporaryDirectory) {
    myProject = project;
    myTempFiles = temporaryDirectory;
  }

  public void tearDown() {
    if (myModule != null) {
      disposeModule(myModule);
      myModule = null;
    }
  }

  public @NotNull PsiPackage createPackage(@NotNull String name) throws IOException {
    Path dir = mySrc.resolve(name);
    Files.createDirectories(dir);
    LocalFileSystem.getInstance().refreshAndFindFileByNioFile(dir);
    return findPackage(name);
  }

  public PsiPackage findPackage(@NotNull String name) {
    return JavaPsiFacade.getInstance(myProject).findPackage(name);
  }

  public @NotNull PsiClass createClass(@NotNull String className, @NotNull String code) throws IOException {
    Path file = mySrc.resolve(className + ".java");
    try (PrintStream stream = new PrintStream(Files.newOutputStream(file))) {
      stream.println(code);
    }
    LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file);
    return JavaPsiFacade.getInstance(myProject).findClass(className, GlobalSearchScope.allScope(myProject));
  }

  public void initModule() throws IOException {
    if (myModule != null) {
      disposeModule(myModule);
    }

    mySrc = myTempFiles.newPath();
    Files.createDirectories(mySrc);
    VirtualFile moduleContent = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(mySrc);

    myModule = BaseConfigurationTestCase.createTempModule(myTempFiles, myProject);
    PsiTestUtil.addSourceRoot(myModule, moduleContent);

    Module tempModule = BaseConfigurationTestCase.createTempModule(myTempFiles, myProject);
    ModuleRootModificationUtil.addDependency(myModule, tempModule);
    disposeModule(tempModule);
  }

  private void disposeModule(@NotNull Module tempModule) {
    ModuleManager.getInstance(myProject).disposeModule(tempModule);
  }

  public void copyJdkFrom(@NotNull Module module) {
    ModuleRootModificationUtil.setModuleSdk(myModule, ModuleRootManager.getInstance(module).getSdk());
  }

  public void addLibrary(@NotNull VirtualFile lib) {
    ModuleRootModificationUtil.addModuleLibrary(myModule, lib.getUrl());
  }

  @NotNull
  public VirtualFile createPackageDir(@NotNull String packageName) throws IOException {
    Path pkg = mySrc.resolve(packageName);
    Files.createDirectories(pkg);
    return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(pkg);
  }

  public PsiClass findClass(@NotNull String fqName) {
    return JavaPsiFacade.getInstance(myProject).findClass(fqName, GlobalSearchScope.moduleScope(myModule));
  }
}
