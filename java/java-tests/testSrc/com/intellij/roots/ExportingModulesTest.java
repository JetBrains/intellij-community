/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.PsiTestUtil;

import java.io.File;

/**
 * @author dsl
 */
public class ExportingModulesTest extends IdeaTestCase {
  public void test1() {
    final String rootPath = PathManagerEx.getTestDataPath().replace(File.separatorChar, '/') + "/moduleRootManager/exportedModules/";
    final VirtualFile testRoot = LocalFileSystem.getInstance().refreshAndFindFileByPath(rootPath);
    assertNotNull(testRoot);

    ApplicationManager.getApplication().runWriteAction(() -> {
      final ModifiableModuleModel moduleModel = ModuleManager.getInstance(myProject).getModifiableModel();
      final Module moduleA = moduleModel.newModule("A.iml", StdModuleTypes.JAVA.getId());
      final Module moduleB = moduleModel.newModule("B.iml", StdModuleTypes.JAVA.getId());
      final Module moduleC = moduleModel.newModule("C.iml", StdModuleTypes.JAVA.getId());
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
    final VirtualFile contentRoot = testRoot.findChild(name);
    PsiTestUtil.addContentRoot(module, contentRoot);
    PsiTestUtil.addSourceRoot(module, contentRoot.findChild("src"));
  }
}
