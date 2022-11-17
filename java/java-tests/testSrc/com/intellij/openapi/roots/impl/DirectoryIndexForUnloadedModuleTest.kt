// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.ide.impl.runUnderModalProgressIfIsEdt
import com.intellij.openapi.module.ModuleManager.Companion.getInstance
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.UsefulTestCase
import java.io.IOException
import java.util.*

class DirectoryIndexForUnloadedModuleTest : DirectoryIndexTestCase() {
  @Throws(IOException::class)
  fun testUnloadedModule() {
    val unloadedModule = createModule("unloaded")
    val root = createTempDirectory()
    val contentRoot = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(root)
    ModuleRootModificationUtil.addContentRoot(unloadedModule, contentRoot!!.path)
    val file = HeavyPlatformTestCase.createChildData(contentRoot, "a.txt")
    assertInProject(file)
    runUnderModalProgressIfIsEdt {
      getInstance(myProject).setUnloadedModules(Arrays.asList("unloaded"))
    }
    assertFromUnloadedModule(file, "unloaded")
    assertFromUnloadedModule(contentRoot, "unloaded")
  }

  fun testDependentUnloadedModules() {
    val unloadedModule = createModule("unloaded")
    val main = createModule("main")
    val util = createModule("util")
    val common = createModule("common")
    ModuleRootModificationUtil.addDependency(unloadedModule, main)
    ModuleRootModificationUtil.addDependency(main, util)
    ModuleRootModificationUtil.addDependency(main, common, DependencyScope.COMPILE, true)
    runUnderModalProgressIfIsEdt {
      getInstance(myProject).setUnloadedModules(Arrays.asList("unloaded"))
    }
    UsefulTestCase.assertSameElements(myIndex.getDependentUnloadedModules(main), "unloaded")
    UsefulTestCase.assertEmpty(myIndex.getDependentUnloadedModules(util))
    UsefulTestCase.assertSameElements(myIndex.getDependentUnloadedModules(common), "unloaded")
  }

  private fun assertFromUnloadedModule(file: VirtualFile, moduleName: String) {
    assertFalse(myFileIndex.isInProject(file))
    assertNull(myFileIndex.getModuleForFile(file))
    assertEquals(moduleName, myIndex.getInfoForFile(file).unloadedModuleName)
  }
}