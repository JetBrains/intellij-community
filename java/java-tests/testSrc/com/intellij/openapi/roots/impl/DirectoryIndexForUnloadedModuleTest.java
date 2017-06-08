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

/**
 * @author nik
 */
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
