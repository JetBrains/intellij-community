/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Collection;

public class TestSources {
  private final Project myProject;
  private final TempFiles myTempFiles;
  private File mySrc;
  private Module myModule;

  public TestSources(Project project, Collection<File> filesToDelete) {
    myProject = project;
    myTempFiles = new TempFiles(filesToDelete);
  }

  public void tearDown() {
    if (myModule != null) {
      disposeModule(myModule);
      myModule = null;
    }
  }

  public PsiPackage createPackage(String name) {
    File dir = new File(mySrc, name);
    dir.mkdir();
    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dir);
    return findPackage(name);
  }

  public PsiPackage findPackage(String name) {
    return JavaPsiFacade.getInstance(myProject).findPackage(name);
  }

  @Nullable
  public PsiClass createClass(String className, String code) throws FileNotFoundException {
    File file = new File(mySrc, className + ".java");
    PrintStream stream = new PrintStream(new FileOutputStream(file));
    try {
      stream.println(code);
    }
    finally {
      stream.close();
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

  private void disposeModule(Module tempModule) {
    ModuleManager.getInstance(myProject).disposeModule(tempModule);
  }

  public void copyJdkFrom(Module module) {
    ModuleRootModificationUtil.setModuleSdk(myModule, ModuleRootManager.getInstance(module).getSdk());
  }

  public void addLibrary(VirtualFile lib) {
    ModuleRootModificationUtil.addModuleLibrary(myModule, lib.getUrl());
  }

  public VirtualFile createPackageDir(String packageName) {
    File pkg = new File(mySrc, packageName);
    pkg.mkdirs();
    VirtualFile pkgFile = TempFiles.getVFileByFile(pkg);
    return pkgFile;
  }

  public PsiClass findClass(String fqName) {
    return JavaPsiFacade.getInstance(myProject).findClass(fqName, GlobalSearchScope.moduleScope(myModule));
  }
}
