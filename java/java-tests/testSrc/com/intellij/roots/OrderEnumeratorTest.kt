// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.roots

import com.intellij.openapi.roots.*
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.util.ArrayUtilRt

class OrderEnumeratorTest : ModuleRootManagerTestCase() {
  fun testLibrary() {
    ModuleRootModificationUtil.addDependency(myModule, createJDomLibrary())
    assertClassRoots(OrderEnumerator.orderEntries(myModule), rtJarJdk17, jDomJar)
    assertClassRoots(OrderEnumerator.orderEntries(myModule).withoutSdk(), jDomJar)
    assertClassRoots(OrderEnumerator.orderEntries(myModule).withoutSdk().productionOnly().runtimeOnly(),
                                                   jDomJar)
    assertClassRoots(OrderEnumerator.orderEntries(myModule).withoutLibraries(), rtJarJdk17)
    assertSourceRoots(OrderEnumerator.orderEntries(myModule), jDomSources)
  }

  fun testModuleSources() {
    val srcRoot = addSourceRoot(myModule, false)
    val testRoot = addSourceRoot(myModule, true)
    val output = setModuleOutput(myModule, false)
    val testOutput = setModuleOutput(myModule, true)
    assertClassRoots(OrderEnumerator.orderEntries(myModule).withoutSdk(), testOutput, output)
    assertClassRoots(OrderEnumerator.orderEntries(myModule).withoutSdk().productionOnly(), output)
    assertSourceRoots(OrderEnumerator.orderEntries(myModule), srcRoot, testRoot)
    assertSourceRoots(OrderEnumerator.orderEntries(myModule).productionOnly(), srcRoot)
    assertEnumeratorRoots(
      OrderEnumerator.orderEntries(myModule).withoutSdk().classes().withoutSelfModuleOutput(), output)
    assertEnumeratorRoots(
      OrderEnumerator.orderEntries(myModule).withoutSdk().productionOnly().classes().withoutSelfModuleOutput())
  }

  fun testLibraryScope() {
    ModuleRootModificationUtil.addDependency(myModule, createJDomLibrary(), DependencyScope.RUNTIME, false)
    assertClassRoots(OrderEnumerator.orderEntries(myModule).withoutSdk(), jDomJar)
    assertClassRoots(OrderEnumerator.orderEntries(myModule).withoutSdk().exportedOnly())
    assertClassRoots(OrderEnumerator.orderEntries(myModule).withoutSdk().compileOnly())
  }

  fun testModuleDependency() {
    val dep = createModule("dep")
    val depSrcRoot = addSourceRoot(dep, false)
    val depTestRoot = addSourceRoot(dep, true)
    val depOutput = setModuleOutput(dep, false)
    val depTestOutput = setModuleOutput(dep, true)
    ModuleRootModificationUtil.addDependency(dep, createJDomLibrary(), DependencyScope.COMPILE, true)
    ModuleRootModificationUtil.addDependency(myModule, dep, DependencyScope.COMPILE, true)
    val srcRoot = addSourceRoot(myModule, false)
    val testRoot = addSourceRoot(myModule, true)
    val output = setModuleOutput(myModule, false)
    val testOutput = setModuleOutput(myModule, true)
    assertClassRoots(OrderEnumerator.orderEntries(myModule).withoutSdk(), testOutput, output, depTestOutput,
                                                   depOutput)
    assertClassRoots(OrderEnumerator.orderEntries(myModule).withoutSdk().recursively(), testOutput, output,
                                                   depTestOutput, depOutput, jDomJar)
    assertSourceRoots(OrderEnumerator.orderEntries(myModule), srcRoot, testRoot, depSrcRoot, depTestRoot)
    assertSourceRoots(OrderEnumerator.orderEntries(myModule).recursively(), srcRoot, testRoot, depSrcRoot,
                                                    depTestRoot, jDomSources)
    assertClassRoots(
      OrderEnumerator.orderEntries(myModule).withoutSdk().withoutModuleSourceEntries().recursively(), jDomJar)
    assertSourceRoots(
      OrderEnumerator.orderEntries(myModule).withoutSdk().withoutModuleSourceEntries().recursively(), jDomSources)
    assertEnumeratorRoots(
      OrderEnumerator.orderEntries(myModule).withoutSdk().withoutModuleSourceEntries().recursively().classes(), jDomJar)
    assertEnumeratorRoots(
      OrderEnumerator.orderEntries(myModule).withoutSdk().withoutModuleSourceEntries().recursively().sources(), jDomSources)
    assertEnumeratorRoots(
      OrderEnumerator.orderEntries(myModule).withoutSdk().recursively().classes().withoutSelfModuleOutput(),
      output, depTestOutput, depOutput, jDomJar)
    assertEnumeratorRoots(
      OrderEnumerator.orderEntries(myModule).productionOnly().withoutSdk().recursively().classes().withoutSelfModuleOutput(),
      depOutput, jDomJar)
    assertClassRoots(
      OrderEnumerator.orderEntries(myModule).withoutSdk().withoutDepModules().withoutModuleSourceEntries().recursively(), jDomJar)
    assertEnumeratorRoots(
      OrderEnumerator.orderEntries(
        myModule).productionOnly().withoutSdk().withoutDepModules().withoutModuleSourceEntries().recursively().classes(),
      jDomJar)
    assertClassRoots(
      OrderEnumerator.orderEntries(myModule).withoutSdk().withoutDepModules().withoutModuleSourceEntries())
    assertEnumeratorRoots(
      OrderEnumerator.orderEntries(myModule).productionOnly().withoutModuleSourceEntries().withoutSdk().withoutDepModules().classes())
    assertOrderedEquals(OrderEnumerator.orderEntries(myModule).allLibrariesAndSdkClassesRoots, rtJarJdk17, jDomJar)
  }

  fun testModuleDependencyScope() {
    val dep = createModule("dep")
    ModuleRootModificationUtil.addDependency(dep, createJDomLibrary(), DependencyScope.COMPILE, true)
    ModuleRootModificationUtil.addDependency(myModule, dep, DependencyScope.TEST, true)
    assertClassRoots(OrderEnumerator.orderEntries(myModule).withoutSdk())
    assertClassRoots(OrderEnumerator.orderEntries(myModule).withoutSdk().recursively(), jDomJar)
    assertClassRoots(OrderEnumerator.orderEntries(myModule).withoutSdk().exportedOnly().recursively(),
                                                   jDomJar)
    assertClassRoots(OrderEnumerator.orderEntries(myModule).withoutSdk().productionOnly().recursively())
    assertClassRoots(ProjectRootManager.getInstance(myProject).orderEntries().withoutSdk(), jDomJar)
    assertClassRoots(ProjectRootManager.getInstance(myProject).orderEntries().withoutSdk().productionOnly(),
                                                   jDomJar)
  }

  fun testNotExportedLibrary() {
    val dep = createModule("dep")
    ModuleRootModificationUtil.addDependency(dep, createJDomLibrary(), DependencyScope.COMPILE, false)
    ModuleRootModificationUtil.addDependency(myModule, createAsmLibrary(), DependencyScope.COMPILE, false)
    ModuleRootModificationUtil.addDependency(myModule, dep, DependencyScope.COMPILE, false)
    assertClassRoots(OrderEnumerator.orderEntries(myModule).withoutSdk(), asmJar)
    assertClassRoots(OrderEnumerator.orderEntries(myModule).withoutSdk().recursively(), asmJar, jDomJar)
    assertClassRoots(OrderEnumerator.orderEntries(myModule).withoutSdk().recursively().exportedOnly(), asmJar)
    assertClassRoots(OrderEnumerator.orderEntries(myModule).withoutSdk().exportedOnly().recursively())
    assertClassRoots(orderEntriesForModulesList(myModule).withoutSdk().recursively(), asmJar, jDomJar)
  }

  fun testJdkIsNotExported() {
    assertClassRoots(OrderEnumerator.orderEntries(myModule).exportedOnly())
  }

  fun testCaching() {
    val jdkRoot = rtJarJdk17
    val roots = OrderEnumerator.orderEntries(myModule).classes().usingCache().roots
    assertOrderedEquals(roots, jdkRoot)
    assertEquals(roots, OrderEnumerator.orderEntries(myModule).classes().usingCache().roots)
    val rootsWithoutSdk = OrderEnumerator.orderEntries(myModule).withoutSdk().classes().usingCache().roots
    assertEmpty(rootsWithoutSdk)
    assertEquals(roots, OrderEnumerator.orderEntries(myModule).classes().usingCache().roots)
    assertEquals(rootsWithoutSdk, OrderEnumerator.orderEntries(myModule).withoutSdk().classes().usingCache().roots)
    ModuleRootModificationUtil.addDependency(myModule, createJDomLibrary())
    assertRoots(OrderEnumerator.orderEntries(myModule).classes().usingCache().pathsList, jdkRoot, jDomJar)
    assertRoots(OrderEnumerator.orderEntries(myModule).withoutSdk().classes().usingCache().pathsList, jDomJar)
  }

  fun testCachingUrls() {
    val jdkUrl = rtJarJdk17.url
    val computedUrls = com.intellij.util.containers.ContainerUtil.map(
      OrderEnumerator.orderEntries(myModule).classes().usingCache().roots) { obj: VirtualFile -> obj.url }
    val urls = OrderEnumerator.orderEntries(myModule).classes().usingCache().urls
    assertOrderedEquals(computedUrls, *urls)
    assertOrderedEquals(urls, jdkUrl)
    assertSame(urls, OrderEnumerator.orderEntries(myModule).classes().usingCache().urls)
    val sourceUrls = OrderEnumerator.orderEntries(myModule).sources().usingCache().urls
    assertEmpty(sourceUrls)
    assertSame(urls, OrderEnumerator.orderEntries(myModule).classes().usingCache().urls)
    assertSame(sourceUrls, OrderEnumerator.orderEntries(myModule).sources().usingCache().urls)
    ModuleRootModificationUtil.addDependency(myModule, createJDomLibrary())
    assertOrderedEquals(OrderEnumerator.orderEntries(myModule).classes().usingCache().urls, jdkUrl, jDomJar.url)
    assertOrderedEquals(OrderEnumerator.orderEntries(myModule).sources().usingCache().urls, jDomSources.url)
  }

  fun testProject() {
    ModuleRootModificationUtil.addDependency(myModule, createJDomLibrary())
    val srcRoot = addSourceRoot(myModule, false)
    val testRoot = addSourceRoot(myModule, true)
    val output = setModuleOutput(myModule, false)
    val testOutput = setModuleOutput(myModule, true)
    assertClassRoots(OrderEnumerator.orderEntries(myProject).withoutSdk(), testOutput, output, jDomJar)
    assertSourceRoots(OrderEnumerator.orderEntries(myProject).withoutSdk(), srcRoot, testRoot, jDomSources)
    val modules: MutableList<com.intellij.openapi.module.Module> = java.util.ArrayList()
    OrderEnumerator.orderEntries(myProject).forEachModule { e: com.intellij.openapi.module.Module -> modules.add(e) }
    assertSameElements(modules, myModule)
  }

  fun testModules() {
    ModuleRootModificationUtil.addDependency(myModule, createJDomLibrary())
    val srcRoot = addSourceRoot(myModule, false)
    val testRoot = addSourceRoot(myModule, true)
    val output = setModuleOutput(myModule, false)
    val testOutput = setModuleOutput(myModule, true)
    assertClassRoots(orderEntriesForModulesList(myModule).withoutSdk(),
                                                   testOutput, output, jDomJar)
    assertSourceRoots(orderEntriesForModulesList(myModule).withoutSdk(),
                                                    srcRoot, testRoot, jDomSources)
  }

  private fun orderEntriesForModulesList(module: com.intellij.openapi.module.Module): OrderEnumerator {
    return ProjectRootManager.getInstance(myProject).orderEntries(listOf(module))
  }

  fun testDoNotAddJdkRootFromModuleDependency() {
    val dep = createModule("dep")
    ModuleRootModificationUtil.addDependency(myModule, dep)
    ModuleRootModificationUtil.setModuleSdk(dep, getMockJdk17WithRtJarOnly())
    ModuleRootModificationUtil.setModuleSdk(myModule, mockJdk18WithRtJarOnly)
    registerTestProjectJdk(getMockJdk17WithRtJarOnly())
    registerTestProjectJdk(mockJdk18WithRtJarOnly)
    assertClassRoots(OrderEnumerator.orderEntries(dep), rtJarJdk17)
    assertClassRoots(OrderEnumerator.orderEntries(myModule).recursively(), rtJarJdk18)
  }

  fun testModuleWithNoTestsNoProductionMustProvideNoOutputRoots() {
    addModuleRoots(false, false)
    assertClassUrls()
  }

  private fun assertClassUrls(vararg expectedUrls: String) {
    val actual = OrderEnumerator.orderEntries(myModule).classes().usingCache().urls
    assertSameElements(actual, *expectedUrls)
  }

  fun testModuleWithNoTestsMustNotProvideTestOutputRoots() {
    addModuleRoots(true, false)
    assertClassUrls(outputUrl(false))
  }

  private fun outputUrl(isTest: Boolean): String {
    return CompilerProjectExtension.getInstance(
      myProject)!!.compilerOutputUrl + "/" + (if (isTest) "test" else "production") + "/" + myModule.name
  }

  fun testModuleWithTestsButNoProductionMustNotProvideProductionOutputRoots() {
    addModuleRoots(false, true)
    assertClassUrls(outputUrl(true))
  }

  fun testModuleWithTestsAndProductionMustProvideBothOutputRoots() {
    addModuleRoots(true, true)
    assertClassUrls(outputUrl(false), outputUrl(true))
  }

  private fun addModuleRoots(addSources: Boolean, addTests: Boolean) {
    val tmp = tempDir.createVirtualDir()
    val contDir = HeavyPlatformTestCase.createChildDirectory(tmp, "content")
    val outDir = HeavyPlatformTestCase.createChildDirectory(tmp, "out")
    CompilerProjectExtension.getInstance(myProject)!!.compilerOutputUrl = outDir.url
    CompilerModuleExtension.getInstance(myModule)!!.inheritCompilerOutputPath(true)
    ModuleRootModificationUtil.updateModel(myModule) { model: ModifiableRootModel ->
      val content = model.addContentEntry(contDir)
      if (addSources) {
        content.addSourceFolder(contDir.url + "/src", false)
      }
      if (addTests) {
        content.addSourceFolder(contDir.url + "/test", true)
      }
      val jdk = model.orderEntries.filterIsInstance<JdkOrderEntry>().first()
      model.removeOrderEntry(jdk)
    }
  }

  companion object {
    private fun assertClassRoots(enumerator: OrderEnumerator, vararg files: VirtualFile) {
      assertEnumeratorRoots(enumerator.classes(), *files)
    }

    private fun assertSourceRoots(enumerator: OrderEnumerator, vararg files: VirtualFile) {
      assertEnumeratorRoots(enumerator.sources(), *files)
    }

    private fun assertEnumeratorRoots(rootsEnumerator: OrderRootsEnumerator, vararg files: VirtualFile) {
      assertOrderedEquals(rootsEnumerator.roots, *files)
      val expectedUrls = files.map { it.url }
      assertOrderedEquals(rootsEnumerator.urls, *ArrayUtilRt.toStringArray(expectedUrls))
    }
  }
}