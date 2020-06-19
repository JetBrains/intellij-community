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
import com.intellij.testFramework.TempFiles;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Collection;

public class TestSources {
  private final Project myProject;
  private final TempFiles myTempFiles;
  private File mySrc;
  private Module myModule;

  public TestSources(@NotNull Project project, @NotNull Collection<Path> filesToDelete) {
    myProject = project;
    myTempFiles = new TempFiles(filesToDelete);
  }

  public void tearDown() {
    if (myModule != null) {
      disposeModule(myModule);
      myModule = null;
    }
  }

  @NotNull
  public PsiPackage createPackage(@NotNull String name) {
    File dir = new File(mySrc, name);
    dir.mkdir();
    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dir);
    return findPackage(name);
  }

  public PsiPackage findPackage(@NotNull String name) {
    return JavaPsiFacade.getInstance(myProject).findPackage(name);
  }

  @NotNull
  public PsiClass createClass(@NotNull String className, @NotNull String code) throws FileNotFoundException {
    File file = new File(mySrc, className + ".java");
    try (PrintStream stream = new PrintStream(new FileOutputStream(file))) {
      stream.println(code);
    }
    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    return JavaPsiFacade.getInstance(myProject).findClass(className, GlobalSearchScope.allScope(myProject));
  }

  public void initModule() {
    if (myModule != null) disposeModule(myModule);
    mySrc = myTempFiles.createTempDir();
    myModule = BaseConfigurationTestCase.createTempModule(myTempFiles, myProject);
    VirtualFile moduleContent = TempFiles.getVFileByFile(mySrc);
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
  public VirtualFile createPackageDir(@NotNull String packageName) {
    File pkg = new File(mySrc, packageName);
    pkg.mkdirs();
    VirtualFile pkgFile = TempFiles.getVFileByFile(pkg);
    return pkgFile;
  }

  public PsiClass findClass(@NotNull String fqName) {
    return JavaPsiFacade.getInstance(myProject).findClass(fqName, GlobalSearchScope.moduleScope(myModule));
  }
}
