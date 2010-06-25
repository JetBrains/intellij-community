/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.roots;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.IdeaTestCase;

import java.io.File;

/**
 * @author dsl
 */
public class ExportingModulesTest extends IdeaTestCase {
  public void test1() throws Exception {
    final String rootPath = PathManagerEx.getTestDataPath().replace(File.separatorChar, '/') + "/moduleRootManager/exportedModules/";
    final VirtualFile testRoot = LocalFileSystem.getInstance().refreshAndFindFileByPath(rootPath);
    assertNotNull(testRoot);

    final ModifiableModuleModel moduleModel = ModuleManager.getInstance(myProject).getModifiableModel();
    final Module moduleA = moduleModel.newModule("A.iml", StdModuleTypes.JAVA);
    final Module moduleB = moduleModel.newModule("B.iml", StdModuleTypes.JAVA);
    final Module moduleC = moduleModel.newModule("C.iml", StdModuleTypes.JAVA);
    moduleModel.commit();

    configureModule(moduleA, testRoot, "A");
    configureModule(moduleB, testRoot, "B");
    configureModule(moduleC, testRoot, "C");

    final ModifiableRootModel rootModelB = ModuleRootManager.getInstance(moduleB).getModifiableModel();
    final ModuleOrderEntry moduleBAentry = rootModelB.addModuleOrderEntry(moduleA);
    moduleBAentry.setExported(true);
    rootModelB.commit();

    final ModifiableRootModel rootModelC = ModuleRootManager.getInstance(moduleC).getModifiableModel();
    rootModelC.addModuleOrderEntry(moduleB);
    rootModelC.commit();

    final PsiClass pCClass =
      JavaPsiFacade.getInstance(myProject).findClass("p.C", GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(moduleC));
    assertNotNull(pCClass);

    final PsiClass pAClass =
      JavaPsiFacade.getInstance(myProject).findClass("p.A", GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(moduleB));
    assertNotNull(pAClass);

    final PsiClass pAClass2 =
      JavaPsiFacade.getInstance(myProject).findClass("p.A", GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(moduleC));
    assertNotNull(pAClass2);

  }

  private void configureModule(final Module module, final VirtualFile testRoot, final String name) {
    final ModifiableRootModel rootModel = ModuleRootManager.getInstance(module).getModifiableModel();
    final VirtualFile contentRoot = testRoot.findChild(name);
    final ContentEntry contentEntry = rootModel.addContentEntry(contentRoot);
    contentEntry.addSourceFolder(contentRoot.findChild("src"), false);
    rootModel.commit();
  }
}
