// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler

import com.intellij.compiler.impl.ModuleCompileScope
import com.intellij.configurationStore.runInAllowSaveMode
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.util.io.TestFileSystemBuilder
import java.io.IOException

class UnloadedModulesCompilationTest : BaseCompilerTestCase() {
  fun testDoNotCompileUnloadedModulesByDefault() {
    val a = createFile("unloaded/src/A.java", "class A{ error }")
    val unloaded = addModule("unloaded", a.parent)
    val unloadedList = listOf(unloaded.name)
    runWithModalProgressBlocking(project, "") {
      ModuleManager.getInstance(myProject).setUnloadedModules(unloadedList)
    }
    buildAllModules().assertUpToDate()
  }

  fun testCompileUnloadedModulesIfExplicitlySpecified() {
    val a = createFile("unloaded/src/A.java", "class A{}")
    val unloaded = addModule("unloaded", a.parent)
    val outputDir = getOutputDir(unloaded, false)
    val unloadedList = listOf(unloaded.name)
    runInAllowSaveMode(true) {
      runWithModalProgressBlocking(project, "") {
        ModuleManager.getInstance(myProject).setUnloadedModules(unloadedList)
      }
    }
    make(createScopeWithUnloaded(emptyList(), unloadedList))
    TestFileSystemBuilder.fs().file("A.class").build().assertDirectoryEqual(outputDir)
  }

  @Throws(IOException::class)
  fun testCompileUsagesOfConstantInUnloadedModules() {
    val utilFile = createFile("unloaded/src/Util.java", "class Util { public static final String FOO = \"foo\"; }")
    val a = createFile("unloaded/src/A.java", "class A{ { System.out.println(Util.FOO); } }")
    val unloaded = addModule("unloaded", a.parent)
    buildAllModules()
    val unloadedList = listOf(unloaded.name)
    runWithModalProgressBlocking(project, "") {
      ModuleManager.getInstance(myProject).setUnloadedModules(unloadedList)
    }
    changeFile(utilFile, VfsUtilCore.loadText(utilFile).replace("foo", "foo2"))
    make(createScopeWithUnloaded(emptyList(), unloadedList)).assertGenerated("A.class", "Util.class")
  }

  @Throws(IOException::class)
  fun testCompileUsagesOfConstantFromNormalModuleInInUnloadedModules() {
    val utilFile = createFile("util/src/Util.java", "class Util { public static final String FOO = \"foo\"; }")
    val util = addModule("util", utilFile.parent)
    val a = createFile("unloaded/src/A.java", "class A{ { System.out.println(Util.FOO); } }")
    val unloaded = addModule("unloaded", a.parent)
    ModuleRootModificationUtil.addDependency(unloaded, util)
    buildAllModules()
    val unloadedList = listOf(unloaded.name)
    runWithModalProgressBlocking(project, "") {
      ModuleManager.getInstance(myProject).setUnloadedModules(unloadedList)
    }
    changeFile(utilFile, VfsUtilCore.loadText(utilFile).replace("foo", "foo2"))
    make(createScopeWithUnloaded(listOf(util), unloadedList)).assertGenerated("A.class", "Util.class")
  }

  fun testCompileUnloadedModuleAfterBuildingAllLoadedModules() {
    val utilFile = createFile("util/src/Util.java", "class Util { }")
    val util = addModule("util", utilFile.parent)
    val a = createFile("unloaded/src/A.java", "class A { Util u = new Util();  }")
    val unloaded = addModule("unloaded", a.parent)
    ModuleRootModificationUtil.addDependency(unloaded, util)
    buildAllModules()
    val unloadedList = listOf(unloaded.name)
    runWithModalProgressBlocking(project, "") {
      ModuleManager.getInstance(myProject).setUnloadedModules(unloadedList)
    }
    changeFile(utilFile, "class Util { Util(int i) {} }")
    buildAllModules().assertGenerated("Util.class")
    compile(createScopeWithUnloaded(listOf(util), unloadedList), false, true)
  }

  private fun createScopeWithUnloaded(modules: List<Module>, unloaded: List<String>): ModuleCompileScope {
    return ModuleCompileScope(myProject, modules, unloaded, true, false)
  }
}