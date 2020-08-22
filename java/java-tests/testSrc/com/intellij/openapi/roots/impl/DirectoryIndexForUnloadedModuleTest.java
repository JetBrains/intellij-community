// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class DirectoryIndexForUnloadedModuleTest extends DirectoryIndexTestCase {
  public void testUnloadedModule() throws IOException {
    Module unloadedModule = createModule("unloaded");
    final File root = createTempDirectory();
    VirtualFile contentRoot = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(root);
    ModuleRootModificationUtil.addContentRoot(unloadedModule, contentRoot.getPath());
    VirtualFile file = createChildData(contentRoot, "a.txt");
    assertInProject(file);

    ModuleManager.getInstance(myProject).setUnloadedModules(Arrays.asList("unloaded"));

    assertFromUnloadedModule(file, "unloaded");
    assertFromUnloadedModule(contentRoot, "unloaded");
  }

  public void testDependentUnloadedModules() {
    Module unloadedModule = createModule("unloaded");
    Module main = createModule("main");
    Module util = createModule("util");
    Module common = createModule("common");
    ModuleRootModificationUtil.addDependency(unloadedModule, main);
    ModuleRootModificationUtil.addDependency(main, util);
    ModuleRootModificationUtil.addDependency(main, common, DependencyScope.COMPILE, true);
    ModuleManager.getInstance(myProject).setUnloadedModules(Arrays.asList("unloaded"));

    assertSameElements(myIndex.getDependentUnloadedModules(main), "unloaded");
    assertEmpty(myIndex.getDependentUnloadedModules(util));
    assertSameElements(myIndex.getDependentUnloadedModules(common), "unloaded");
  }

  private void assertFromUnloadedModule(VirtualFile file, String moduleName) {
    DirectoryInfo info = myIndex.getInfoForFile(file);
    assertTrue(info.toString(), info.isExcluded(file));
    assertNull(info.getModule());
    assertEquals(moduleName, info.getUnloadedModuleName());
  }
}
