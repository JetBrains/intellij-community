// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.execution;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.TemporaryDirectory;
import com.intellij.testFramework.VfsTestUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

final class TestSources {
  private final Project myProject;
  private final TemporaryDirectory tempDir;
  private VirtualFile mySrc;
  private Module myModule;

  TestSources(@NotNull Project project, @NotNull TemporaryDirectory temporaryDirectory) {
    myProject = project;
    tempDir = temporaryDirectory;
  }

  public void tearDown() {
    if (myModule != null) {
      disposeModule(myModule);
      myModule = null;
    }
  }

  public @NotNull PsiPackage createPackage(@NotNull String name) {
    VfsTestUtil.createDir(mySrc, name);
    return findPackage(name);
  }

  public PsiPackage findPackage(@NotNull String name) {
    return JavaPsiFacade.getInstance(myProject).findPackage(name);
  }

  public @NotNull PsiClass createClass(@NotNull String className, @NotNull @Language("JAVA") String code) {
    VfsTestUtil.createFile(mySrc, className + ".java", code + System.lineSeparator());
    return JavaPsiFacade.getInstance(myProject).findClass(className, GlobalSearchScope.allScope(myProject));
  }

  public void initModule() {
    if (myModule != null) {
      disposeModule(myModule);
    }

    mySrc = tempDir.createVirtualDir();
    myModule = BaseConfigurationTestCase.createTempModule(tempDir, myProject);
    PsiTestUtil.addSourceRoot(myModule, mySrc);

    Module tempModule = BaseConfigurationTestCase.createTempModule(tempDir, myProject);
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

  public @NotNull VirtualFile createPackageDir(@NotNull String packageName) {
    VirtualFile result = mySrc.findChild(packageName);
    return result == null ? VfsTestUtil.createDir(mySrc, packageName) : result;
  }

  public PsiClass findClass(@NotNull String fqName) {
    return JavaPsiFacade.getInstance(myProject).findClass(fqName, GlobalSearchScope.moduleScope(myModule));
  }
}
