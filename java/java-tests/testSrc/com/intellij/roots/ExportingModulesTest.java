// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.roots;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.project.ProjectKt;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.JavaProjectTestCase;
import com.intellij.testFramework.PsiTestUtil;

import java.io.File;
import java.nio.file.Path;

public class ExportingModulesTest extends JavaProjectTestCase {
  public void test1() {
    String rootPath = PathManagerEx.getTestDataPath().replace(File.separatorChar, '/') + "/moduleRootManager/exportedModules/";
    VirtualFile testRoot = LocalFileSystem.getInstance().refreshAndFindFileByPath(rootPath);
    assertNotNull(testRoot);

    Path dir = ProjectKt.getStateStore(myProject).getProjectBasePath();
    ApplicationManager.getApplication().runWriteAction(() -> {
      ModifiableModuleModel moduleModel = ModuleManager.getInstance(myProject).getModifiableModel();
      Module moduleA = moduleModel.newModule(dir.resolve("A.iml"), StdModuleTypes.JAVA.getId());
      Module moduleB = moduleModel.newModule(dir.resolve("B.iml"), StdModuleTypes.JAVA.getId());
      Module moduleC = moduleModel.newModule(dir.resolve("C.iml"), StdModuleTypes.JAVA.getId());
      moduleModel.commit();

      configureModule(moduleA, testRoot, "A");
      configureModule(moduleB, testRoot, "B");
      configureModule(moduleC, testRoot, "C");

      ModuleRootModificationUtil.addDependency(moduleB, moduleA, DependencyScope.COMPILE, true);

      ModuleRootModificationUtil.addDependency(moduleC, moduleB);

      final PsiClass pCClass =
        JavaPsiFacade.getInstance(myProject).findClass("p.C", GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(moduleC));
      assertNotNull(pCClass);

      final PsiClass pAClass =
        JavaPsiFacade.getInstance(myProject).findClass("p.A", GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(moduleB));
      assertNotNull(pAClass);

      final PsiClass pAClass2 =
        JavaPsiFacade.getInstance(myProject).findClass("p.A", GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(moduleC));
      assertNotNull(pAClass2);
    });
  }

  private static void configureModule(final Module module, final VirtualFile testRoot, final String name) {
    VirtualFile contentRoot = testRoot.findChild(name);
    PsiTestUtil.addContentRoot(module, contentRoot);
    PsiTestUtil.addSourceRoot(module, contentRoot.findChild("src"));
  }
}
