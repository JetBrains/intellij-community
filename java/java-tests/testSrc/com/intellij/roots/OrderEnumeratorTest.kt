// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.roots

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.roots.ModuleRootManagerTestCase.assertRoots
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.UsefulTestCase.*
import com.intellij.testFramework.rules.ProjectModelRule
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

class OrderEnumeratorTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @Rule
  @JvmField
  val projectModel = ProjectModelRule()

  private lateinit var module: Module
  private lateinit var library: Library
  private lateinit var jdkJar: VirtualFile
  private lateinit var libraryJar: VirtualFile
  private lateinit var librarySourcesZip: VirtualFile
  private lateinit var sdk: Sdk

  @Before
  fun setUp() {
    module = projectModel.createModule()
    libraryJar = projectModel.baseProjectDir.newEmptyVirtualJarFile("foo.jar")
    librarySourcesZip = projectModel.baseProjectDir.newEmptyVirtualJarFile("foo.zip")
    library = projectModel.addProjectLevelLibrary("foo") {
      it.addRoot(libraryJar, OrderRootType.CLASSES)
      it.addRoot(librarySourcesZip, OrderRootType.SOURCES)
    }
    jdkJar = projectModel.baseProjectDir.newEmptyVirtualJarFile("jdk.jar")
    sdk = projectModel.addSdk("my-jdk") {
      it.addRoot(jdkJar, OrderRootType.CLASSES)
    }
    ModuleRootModificationUtil.setModuleSdk(module, sdk)
  }

  @Test
  fun testLibrary() {
    ModuleRootModificationUtil.addDependency(module, library)
    assertClassRoots(OrderEnumerator.orderEntries(module), jdkJar, libraryJar)
    assertClassRoots(OrderEnumerator.orderEntries(module).withoutSdk(), libraryJar)
    assertClassRoots(OrderEnumerator.orderEntries(module).withoutSdk().productionOnly().runtimeOnly(),
                     libraryJar)
    assertClassRoots(OrderEnumerator.orderEntries(module).withoutLibraries(), jdkJar)
    assertSourceRoots(OrderEnumerator.orderEntries(module), librarySourcesZip)
  }

  @Test
  fun testModuleSources() {
    val srcRoot = addSourceRoot(module, false)
    val testRoot = addSourceRoot(module, true)
    val output = setModuleOutput(module, false)
    val testOutput = setModuleOutput(module, true)
    assertClassRoots(OrderEnumerator.orderEntries(module).withoutSdk(), testOutput, output)
    assertClassRoots(OrderEnumerator.orderEntries(module).withoutSdk().productionOnly(), output)
    assertSourceRoots(OrderEnumerator.orderEntries(module), srcRoot, testRoot)
    assertSourceRoots(OrderEnumerator.orderEntries(module).productionOnly(), srcRoot)
    assertEnumeratorRoots(
      OrderEnumerator.orderEntries(module).withoutSdk().classes().withoutSelfModuleOutput(), output)
    assertEnumeratorRoots(
      OrderEnumerator.orderEntries(module).withoutSdk().productionOnly().classes().withoutSelfModuleOutput())
  }

  @Test
  fun testLibraryScope() {
    ModuleRootModificationUtil.addDependency(module, library, DependencyScope.RUNTIME, false)
    assertClassRoots(OrderEnumerator.orderEntries(module).withoutSdk(), libraryJar)
    assertClassRoots(OrderEnumerator.orderEntries(module).withoutSdk().exportedOnly())
    assertClassRoots(OrderEnumerator.orderEntries(module).withoutSdk().compileOnly())
  }

  @Test
  fun testModuleDependency() {
    val dep = projectModel.createModule("dep")
    val depSrcRoot = addSourceRoot(dep, false)
    val depTestRoot = addSourceRoot(dep, true)
    val depOutput = setModuleOutput(dep, false)
    val depTestOutput = setModuleOutput(dep, true)
    ModuleRootModificationUtil.addDependency(dep, library, DependencyScope.COMPILE, true)
    ModuleRootModificationUtil.addDependency(module, dep, DependencyScope.COMPILE, true)
    val srcRoot = addSourceRoot(module, false)
    val testRoot = addSourceRoot(module, true)
    val output = setModuleOutput(module, false)
    val testOutput = setModuleOutput(module, true)
    assertClassRoots(OrderEnumerator.orderEntries(module).withoutSdk(), testOutput, output, depTestOutput,
                     depOutput)
    assertClassRoots(OrderEnumerator.orderEntries(module).withoutSdk().recursively(), testOutput, output,
                     depTestOutput, depOutput, libraryJar)
    assertSourceRoots(OrderEnumerator.orderEntries(module), srcRoot, testRoot, depSrcRoot, depTestRoot)
    assertSourceRoots(OrderEnumerator.orderEntries(module).recursively(), srcRoot, testRoot, depSrcRoot,
                      depTestRoot, librarySourcesZip)
    assertClassRoots(
      OrderEnumerator.orderEntries(module).withoutSdk().withoutModuleSourceEntries().recursively(), libraryJar)
    assertSourceRoots(
      OrderEnumerator.orderEntries(module).withoutSdk().withoutModuleSourceEntries().recursively(), librarySourcesZip)
    assertEnumeratorRoots(
      OrderEnumerator.orderEntries(module).withoutSdk().withoutModuleSourceEntries().recursively().classes(), libraryJar)
    assertEnumeratorRoots(
      OrderEnumerator.orderEntries(module).withoutSdk().withoutModuleSourceEntries().recursively().sources(), librarySourcesZip)
    assertEnumeratorRoots(
      OrderEnumerator.orderEntries(module).withoutSdk().recursively().classes().withoutSelfModuleOutput(),
      output, depTestOutput, depOutput, libraryJar)
    assertEnumeratorRoots(
      OrderEnumerator.orderEntries(module).productionOnly().withoutSdk().recursively().classes().withoutSelfModuleOutput(),
      depOutput, libraryJar)
    assertClassRoots(
      OrderEnumerator.orderEntries(module).withoutSdk().withoutDepModules().withoutModuleSourceEntries().recursively(), libraryJar)
    assertEnumeratorRoots(
      OrderEnumerator.orderEntries(
        module).productionOnly().withoutSdk().withoutDepModules().withoutModuleSourceEntries().recursively().classes(),
      libraryJar)
    assertClassRoots(
      OrderEnumerator.orderEntries(module).withoutSdk().withoutDepModules().withoutModuleSourceEntries())
    assertEnumeratorRoots(
      OrderEnumerator.orderEntries(module).productionOnly().withoutModuleSourceEntries().withoutSdk().withoutDepModules().classes())
    assertOrderedEquals(OrderEnumerator.orderEntries(module).allLibrariesAndSdkClassesRoots, jdkJar, libraryJar)
  }

  @Test
  fun testModuleDependencyScope() {
    val dep = projectModel.createModule("dep")
    ModuleRootModificationUtil.addDependency(dep, library, DependencyScope.COMPILE, true)
    ModuleRootModificationUtil.addDependency(module, dep, DependencyScope.TEST, true)
    assertClassRoots(OrderEnumerator.orderEntries(module).withoutSdk())
    assertClassRoots(OrderEnumerator.orderEntries(module).withoutSdk().recursively(), libraryJar)
    assertClassRoots(OrderEnumerator.orderEntries(module).withoutSdk().exportedOnly().recursively(),
                     libraryJar)
    assertClassRoots(OrderEnumerator.orderEntries(module).withoutSdk().productionOnly().recursively())
    assertClassRoots(ProjectRootManager.getInstance(projectModel.project).orderEntries().withoutSdk(), libraryJar)
    assertClassRoots(ProjectRootManager.getInstance(projectModel.project).orderEntries().withoutSdk().productionOnly(),
                     libraryJar)
  }

  @Test
  fun testNotExportedLibrary() {
    val dep = projectModel.createModule("dep")
    val library2Jar = projectModel.baseProjectDir.newEmptyVirtualJarFile("bar.jar")
    val library2 = projectModel.addProjectLevelLibrary("bar") {
      it.addRoot(library2Jar, OrderRootType.CLASSES)
    }

    ModuleRootModificationUtil.addDependency(dep, library, DependencyScope.COMPILE, false)
    ModuleRootModificationUtil.addDependency(module, library2, DependencyScope.COMPILE, false)
    ModuleRootModificationUtil.addDependency(module, dep, DependencyScope.COMPILE, false)
    assertClassRoots(OrderEnumerator.orderEntries(module).withoutSdk(), library2Jar)
    assertClassRoots(OrderEnumerator.orderEntries(module).withoutSdk().recursively(), library2Jar, libraryJar)
    assertClassRoots(OrderEnumerator.orderEntries(module).withoutSdk().recursively().exportedOnly(), library2Jar)
    assertClassRoots(OrderEnumerator.orderEntries(module).withoutSdk().exportedOnly().recursively())
    assertClassRoots(orderEntriesForModulesList(module).withoutSdk().recursively(), library2Jar, libraryJar)
  }

  @Test
  fun testJdkIsNotExported() {
    assertClassRoots(OrderEnumerator.orderEntries(module).exportedOnly())
  }

  @Test
  fun testCaching() {
    val roots = OrderEnumerator.orderEntries(module).classes().usingCache().roots
    assertOrderedEquals(roots, jdkJar)
    assertEquals(roots, OrderEnumerator.orderEntries(module).classes().usingCache().roots)
    val rootsWithoutSdk = OrderEnumerator.orderEntries(module).withoutSdk().classes().usingCache().roots
    assertEmpty(rootsWithoutSdk)
    assertEquals(roots, OrderEnumerator.orderEntries(module).classes().usingCache().roots)
    assertEquals(rootsWithoutSdk, OrderEnumerator.orderEntries(module).withoutSdk().classes().usingCache().roots)
    ModuleRootModificationUtil.addDependency(module, library)
    assertRoots(OrderEnumerator.orderEntries(module).classes().usingCache().pathsList, jdkJar, libraryJar)
    assertRoots(OrderEnumerator.orderEntries(module).withoutSdk().classes().usingCache().pathsList, libraryJar)
  }

  @Test
  fun testCachingUrls() {
    val computedUrls = OrderEnumerator.orderEntries(module).classes().usingCache().roots.map { it.url }
    val urls = OrderEnumerator.orderEntries(module).classes().usingCache().urls
    assertOrderedEquals(computedUrls, *urls)
    assertOrderedEquals(urls, jdkJar.url)
    assertSame(urls, OrderEnumerator.orderEntries(module).classes().usingCache().urls)
    val sourceUrls = OrderEnumerator.orderEntries(module).sources().usingCache().urls
    assertEmpty(sourceUrls)
    assertSame(urls, OrderEnumerator.orderEntries(module).classes().usingCache().urls)
    assertSame(sourceUrls, OrderEnumerator.orderEntries(module).sources().usingCache().urls)
    ModuleRootModificationUtil.addDependency(module, library)
    assertOrderedEquals(OrderEnumerator.orderEntries(module).classes().usingCache().urls, jdkJar.url, libraryJar.url)
    assertOrderedEquals(OrderEnumerator.orderEntries(module).sources().usingCache().urls, librarySourcesZip.url)
  }

  @Test
  fun testProject() {
    ModuleRootModificationUtil.addDependency(module, library)
    val srcRoot = addSourceRoot(module, false)
    val testRoot = addSourceRoot(module, true)
    val output = setModuleOutput(module, false)
    val testOutput = setModuleOutput(module, true)
    assertClassRoots(OrderEnumerator.orderEntries(projectModel.project).withoutSdk(), testOutput, output, libraryJar)
    assertSourceRoots(OrderEnumerator.orderEntries(projectModel.project).withoutSdk(), srcRoot, testRoot, librarySourcesZip)
    val modules = ArrayList<Module>()
    runReadAction { OrderEnumerator.orderEntries(projectModel.project).forEachModule { modules.add(it) } }
    assertSameElements(modules, module)
  }

  @Test
  fun testModules() {
    ModuleRootModificationUtil.addDependency(module, library)
    val srcRoot = addSourceRoot(module, false)
    val testRoot = addSourceRoot(module, true)
    val output = setModuleOutput(module, false)
    val testOutput = setModuleOutput(module, true)
    assertClassRoots(orderEntriesForModulesList(module).withoutSdk(),
                     testOutput, output, libraryJar)
    assertSourceRoots(orderEntriesForModulesList(module).withoutSdk(),
                      srcRoot, testRoot, librarySourcesZip)
  }

  private fun orderEntriesForModulesList(module: Module): OrderEnumerator {
    return ProjectRootManager.getInstance(projectModel.project).orderEntries(listOf(module))
  }

  @Test
  fun testDoNotAddJdkRootFromModuleDependency() {
    val dep = projectModel.createModule("dep")
    ModuleRootModificationUtil.addDependency(module, dep)
    val jdk2Jar = projectModel.baseProjectDir.newEmptyVirtualJarFile("jdk2.jar")
    val sdk2 = projectModel.addSdk("my-jdk2") {
      it.addRoot(jdk2Jar, OrderRootType.CLASSES)
    }
    ModuleRootModificationUtil.setModuleSdk(dep, sdk2)
    assertClassRoots(OrderEnumerator.orderEntries(dep), jdk2Jar)
    assertClassRoots(OrderEnumerator.orderEntries(module).recursively(), jdkJar)
  }

  @Test
  fun testModuleWithNoTestsNoProductionMustProvideNoOutputRoots() {
    addModuleRoots(false, false)
    assertClassUrls()
  }

  private fun assertClassUrls(vararg expectedUrls: String) {
    assertSameElements(OrderEnumerator.orderEntries(module).classes().usingCache().urls, *expectedUrls)
  }

  @Test
  fun testModuleWithNoTestsMustNotProvideTestOutputRoots() {
    addModuleRoots(true, false)
    assertClassUrls(outputUrl(false))
  }

  @Test
  fun testModuleWithTestsButNoProductionMustNotProvideProductionOutputRoots() {
    addModuleRoots(false, true)
    assertClassUrls(outputUrl(true))
  }

  @Test
  fun testModuleWithTestsAndProductionMustProvideBothOutputRoots() {
    addModuleRoots(true, true)
    assertClassUrls(outputUrl(false), outputUrl(true))
  }

  private fun addModuleRoots(addSources: Boolean, addTests: Boolean) {
    val contentRoot = projectModel.baseProjectDir.newVirtualDirectory("content")
    val outDir = projectModel.baseProjectDir.newVirtualDirectory("out")
    CompilerProjectExtension.getInstance(projectModel.project)!!.apply {
      runWriteActionAndWait {
        compilerOutputUrl = outDir.url
      }
    }
    ModuleRootModificationUtil.updateModel(module) { model: ModifiableRootModel ->
      val content = model.addContentEntry(contentRoot)
      if (addSources) {
        content.addSourceFolder(contentRoot.url + "/src", false)
      }
      if (addTests) {
        content.addSourceFolder(contentRoot.url + "/test", true)
      }
      model.removeOrderEntry(model.orderEntries.filterIsInstance<JdkOrderEntry>().first())
    }
  }

  private fun outputUrl(isTest: Boolean): String {
    return CompilerProjectExtension.getInstance(projectModel.project)!!.compilerOutputUrl + "/" + (if (isTest) "test" else "production") + "/" + module.name
  }

  private fun addSourceRoot(module: Module, test: Boolean): VirtualFile {
    val path = if (test) "test" else "src"
    val type = if (test) JavaSourceRootType.TEST_SOURCE else JavaSourceRootType.SOURCE
    return projectModel.addSourceRoot(module, path, type)
  }

  private fun setModuleOutput(module: Module, test: Boolean): VirtualFile {
    val output = projectModel.baseProjectDir.newVirtualDirectory("${module.name}/out/${if (test) "tests" else "production"}")
    PsiTestUtil.setCompilerOutputPath(module, output.url, test)
    return output
  }

  private fun assertClassRoots(enumerator: OrderEnumerator, vararg files: VirtualFile) {
    assertEnumeratorRoots(enumerator.classes(), *files)
  }

  private fun assertSourceRoots(enumerator: OrderEnumerator, vararg files: VirtualFile) {
    assertEnumeratorRoots(enumerator.sources(), *files)
  }

  private fun assertEnumeratorRoots(rootsEnumerator: OrderRootsEnumerator, vararg files: VirtualFile) {
    assertOrderedEquals(runReadAction { rootsEnumerator.roots }, *files)
    assertOrderedEquals(runReadAction { rootsEnumerator.urls }, *files.map { it.url }.toTypedArray())
  }
}
