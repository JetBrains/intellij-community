// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.actions

import com.intellij.ide.extractModule.ExtractModuleFromPackageAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.rules.ProjectModelRule
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.invariantSeparatorsPathString

class ExtractModuleFromPackageActionTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @Rule
  @JvmField
  val projectModel = ProjectModelRule()

  @Test
  fun `extract module in place`() {
    val (main, directory) = prepareProject()

    extractModule(directory, main, null)
    val xxx = projectModel.moduleManager.findModuleByName("main.xxx")!!
    val dep1 = projectModel.moduleManager.findModuleByName("dep1")!!
    val dep2 = projectModel.moduleManager.findModuleByName("dep2")!!
    assertThat(xxx.moduleNioFile).isEqualTo(projectModel.baseProjectDir.rootPath.resolve("main/main.xxx.iml"))
    val xxxRoots = ModuleRootManager.getInstance(xxx)
    assertThat(xxxRoots.sourceRoots).containsExactly(directory.virtualFile)
    assertThat(xxxRoots.dependencies).containsExactly(dep1)
    assertThat(xxxRoots.contentEntries.single().sourceFolders.single().packagePrefix).isEqualTo("xxx")
    val mainRoots = ModuleRootManager.getInstance(main)
    assertThat(mainRoots.dependencies).containsExactly(dep2)
  }

  @Test
  fun `extract module to separate directory`() {
    val (main, directory) = prepareProject()

    val targetSourceRoot = projectModel.baseProjectDir.rootPath.resolve("xxx/src").invariantSeparatorsPathString
    extractModule(directory, main, targetSourceRoot)
    val srcRoot = LocalFileSystem.getInstance().findFileByPath(targetSourceRoot)!!
    val xxx = projectModel.moduleManager.findModuleByName("main.xxx")!!
    assertThat(xxx.moduleNioFile).isEqualTo(projectModel.baseProjectDir.rootPath.resolve("xxx/main.xxx.iml"))
    val xxxRoots = ModuleRootManager.getInstance(xxx)
    assertThat(xxxRoots.contentRoots).containsExactly(srcRoot.parent)
    assertThat(xxxRoots.sourceRoots).containsExactly(srcRoot)
    assertThat(xxxRoots.contentEntries.single().sourceFolders.single().packagePrefix).isEqualTo("")
    assertThat(Path.of(targetSourceRoot, "xxx/Main.java")).exists()
    assertThat(projectModel.baseProjectDir.rootPath.resolve("main/src/xxx/Main.java")).doesNotExist()
  }

  @Test
  fun `extract module and replace by exported dependency`() {
    val (main, directory) = prepareProject(addDirectUsageOfExportedModule = true)

    extractModule(directory, main, null)
    val exported = projectModel.moduleManager.findModuleByName("exported")!!
    val dep2 = projectModel.moduleManager.findModuleByName("dep2")!!
    val mainRoots = ModuleRootManager.getInstance(main)
    assertThat(mainRoots.dependencies).containsExactly(dep2, exported)
  }

  private fun extractModule(directory: PsiDirectory, main: Module, targetSourceRoot: String?) {
    val promise = ExtractModuleFromPackageAction.extractModuleFromDirectory(directory, main, "main.xxx",
                                                                            targetSourceRoot)
    promise.blockingGet(10, TimeUnit.SECONDS)
  }

  private fun prepareProject(addDirectUsageOfExportedModule: Boolean = false): Pair<Module, PsiDirectory> {
    val main = projectModel.createModule("main")
    val dep1 = projectModel.createModule("dep1")
    val dep2 = projectModel.createModule("dep2")
    val exported = projectModel.createModule("exported")
    ModuleRootModificationUtil.addDependency(main, dep1)
    ModuleRootModificationUtil.addDependency(dep1, exported, DependencyScope.COMPILE, true)
    ModuleRootModificationUtil.addDependency(main, dep2)
    val mainSrc = projectModel.addSourceRoot(main, "src", JavaSourceRootType.SOURCE)
    val dep1Src = projectModel.addSourceRoot(dep1, "src", JavaSourceRootType.SOURCE)
    val dep2Src = projectModel.addSourceRoot(dep2, "src", JavaSourceRootType.SOURCE)
    val exportedSrc = projectModel.addSourceRoot(exported, "src", JavaSourceRootType.SOURCE)
    val mainClass = VfsTestUtil.createFile(mainSrc, "xxx/Main.java", "package xxx;\nclass Main extends dep1.Dep1 { exported.Util u; }")
    VfsTestUtil.createFile(mainSrc, "main/MyClass.java", "package main;\nclass MyClass {}")
    if (addDirectUsageOfExportedModule) {
      VfsTestUtil.createFile(mainSrc, "main/ExportedUsage.java", "package main;\nclass ExportedUsage { exported.Util u; }")
    }
    VfsTestUtil.createFile(dep1Src, "dep1/Dep1.java", "package dep1;\npublic class Dep1 {}")
    VfsTestUtil.createFile(dep2Src, "dep2/Dep2.java", "package dep2;\npublic class Dep2 {}")
    VfsTestUtil.createFile(exportedSrc, "exported/Util.java", "package exported;\npublic class Util {}")

    val directory = runReadAction { PsiManager.getInstance(projectModel.project).findDirectory(mainClass.parent)!! }
    return Pair(main, directory)
  }
}