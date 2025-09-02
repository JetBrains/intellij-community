// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.actions

import com.intellij.ide.extractModule.ExtractModuleService
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ModuleOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.CompilerTester
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.rules.ProjectModelRule
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path
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

  @Rule
  @JvmField
  val disposableRule = DisposableRule()

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

  @Test
  fun `add dependencies from others modules after extracting`() {
    val (main, directory) = prepareProject()
    val otherModuleWithoutReference = projectModel.createModule("otherWithoutReference")

    val otherModuleWithReference = projectModel.createModule("otherWithReference")
    projectModel.addSourceRoot(otherModuleWithReference, "src", JavaSourceRootType.SOURCE).let {
      VfsTestUtil.createFile(it, "other/Other.java", "package other;\npublic class Other { main.MyClass myClass; xxx.Main main; }")
    }

    val otherModuleWithReferenceOnExtractedOnly = projectModel.createModule("otherWithReferenceOnExtractedOnly")
    projectModel.addSourceRoot(otherModuleWithReferenceOnExtractedOnly, "src", JavaSourceRootType.SOURCE).let {
      VfsTestUtil.createFile(it, "other2/Other2.java", "package other2;\npublic class Other2 { xxx.Main other2; }")
    }

    ModuleRootModificationUtil.addDependency(otherModuleWithoutReference, main)
    ModuleRootModificationUtil.addDependency(otherModuleWithReference, main)
    ModuleRootModificationUtil.addDependency(otherModuleWithReferenceOnExtractedOnly, main)

    extractModule(directory, main, null)
    val extracted = projectModel.moduleManager.findModuleByName("main.xxx")!!

    with(SoftAssertions()) {
      assertThat(ModuleRootManager.getInstance(otherModuleWithReference).dependencies).containsExactly(main, extracted)
      assertThat(ModuleRootManager.getInstance(otherModuleWithoutReference).dependencies).containsExactly(main)
      assertThat(ModuleRootManager.getInstance(otherModuleWithReferenceOnExtractedOnly).dependencies).containsExactly(extracted)
      assertAll()
    }
  }

  @Test
  fun `extract module with test roots`() {
    val (main, directory) = prepareProject()
    val referencesNone = projectModel.createModule("none")

    val referencesBothInTests = projectModel.createModule("bothInTests")
    projectModel.addSourceRoot(referencesBothInTests, "test", JavaSourceRootType.TEST_SOURCE).let {
      VfsTestUtil.createFile(it, "other1/OtherTest.java", "package other1;\npublic class OtherTest { main.MyClass myClass; xxx.Main main; }")
    }

    val referencesModuleInProdExtractedInTests = projectModel.createModule("moduleInProdExtractedInTests")
    projectModel.addSourceRoot(referencesModuleInProdExtractedInTests, "src", JavaSourceRootType.SOURCE).let {
      VfsTestUtil.createFile(it, "other2/Other2.java", "package other2;\npublic class Other2 { main.MyClass myClass; }")
    }
    projectModel.addSourceRoot(referencesModuleInProdExtractedInTests, "test", JavaSourceRootType.TEST_SOURCE).let {
      VfsTestUtil.createFile(it, "other2/Other2Test.java", "package other2;\npublic class Other2Test { xxx.Main main; }")
    }
    
    val referencesExtractedInProdModuleInTests = projectModel.createModule("extractedInProdModuleInTests")
    projectModel.addSourceRoot(referencesExtractedInProdModuleInTests, "src", JavaSourceRootType.SOURCE).let {
      VfsTestUtil.createFile(it, "other3/Other3.java", "package other3;\npublic class Other3 { xxx.Main other3; }")
    }
    projectModel.addSourceRoot(referencesExtractedInProdModuleInTests, "test", JavaSourceRootType.TEST_SOURCE).let {
      VfsTestUtil.createFile(it, "other3/Other3Test.java", "package other3;\npublic class Other3Test { main.MyClass myClass; }")
    }

    ModuleRootModificationUtil.addDependency(referencesNone, main)
    ModuleRootModificationUtil.addDependency(referencesBothInTests, main)
    ModuleRootModificationUtil.addDependency(referencesModuleInProdExtractedInTests, main)
    ModuleRootModificationUtil.addDependency(referencesExtractedInProdModuleInTests, main)

    extractModule(directory, main, null)
    val extracted = projectModel.moduleManager.findModuleByName("main.xxx")!!
    
    with(SoftAssertions()) {
      assertThat(ModuleRootManager.getInstance(referencesNone).dependencies).containsExactly(main)
      
      assertThat(ModuleRootManager.getInstance(referencesBothInTests).getDependencies(false)).isEmpty()
      assertThat(ModuleRootManager.getInstance(referencesBothInTests).getDependencies(true)).containsExactly(main, extracted)

      assertThat(ModuleRootManager.getInstance(referencesModuleInProdExtractedInTests).getDependencies(false)).containsExactly(main)
      assertThat(ModuleRootManager.getInstance(referencesModuleInProdExtractedInTests).getDependencies(true)).containsExactly(main, extracted)

      assertThat(ModuleRootManager.getInstance(referencesExtractedInProdModuleInTests).getDependencies(false)).containsExactly(extracted)
      assertThat(ModuleRootManager.getInstance(referencesExtractedInProdModuleInTests).getDependencies(true)).containsExactly(main, extracted)
      assertAll()
    }
  }

  private fun extractModule(directory: PsiDirectory, main: Module, targetSourceRoot: String?) {
    val compilerTester = CompilerTester(projectModel.project, ModuleManager.getInstance(projectModel.project).modules.toList(), disposableRule.disposable)
    val messages = compilerTester.rebuild()
    assertThat(messages.filter { it.category == CompilerMessageCategory.ERROR }).isEmpty()
    runBlocking {
      projectModel.project.service<ExtractModuleService>().extractModuleFromDirectory(directory, main, "main.xxx",
                                                                                      targetSourceRoot)
    }
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
    val mainClass = VfsTestUtil.createFile(mainSrc, "xxx/Main.java", "package xxx;\npublic class Main extends dep1.Dep1 { exported.Util u; }")
    VfsTestUtil.createFile(mainSrc, "main/MyClass.java", "package main;\npublic class MyClass { dep2.Dep2 d; }")
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