// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.roots;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.project.ProjectKt;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.IndexingTestUtil;
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

    final Module[] moduleB = new Module[1];
    final Module[] moduleC = new Module[1];

    ApplicationManager.getApplication().runWriteAction(() -> {
      ModifiableModuleModel moduleModel = ModuleManager.getInstance(myProject).getModifiableModel();
      Module moduleA = moduleModel.newModule(dir.resolve("A.iml"), JavaModuleType.getModuleType().getId());

      moduleB[0] = moduleModel.newModule(dir.resolve("B.iml"), JavaModuleType.getModuleType().getId());
      moduleC[0] = moduleModel.newModule(dir.resolve("C.iml"), JavaModuleType.getModuleType().getId());
      moduleModel.commit();

      configureModule(moduleA, testRoot, "A");
      configureModule(moduleB[0], testRoot, "B");
      configureModule(moduleC[0], testRoot, "C");

      ModuleRootModificationUtil.addDependency(moduleB[0], moduleA, DependencyScope.COMPILE, true);

      ModuleRootModificationUtil.addDependency(moduleC[0], moduleB[0]);
    });
    IndexingTestUtil.waitUntilIndexesAreReady(getProject());

    final PsiClass pCClass =
      JavaPsiFacade.getInstance(myProject).findClass("p.C", GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(moduleC[0]));
    assertNotNull(pCClass);

    final PsiClass pAClass =
      JavaPsiFacade.getInstance(myProject).findClass("p.A", GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(moduleB[0]));
    assertNotNull(pAClass);

    final PsiClass pAClass2 =
      JavaPsiFacade.getInstance(myProject).findClass("p.A", GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(moduleC[0]));
    assertNotNull(pAClass2);
  }

  private static void configureModule(final Module module, final VirtualFile testRoot, final String name) {
    VirtualFile contentRoot = testRoot.findChild(name);
    PsiTestUtil.addContentRoot(module, contentRoot);
    PsiTestUtil.addSourceRoot(module, contentRoot.findChild("src"));
  }
}
