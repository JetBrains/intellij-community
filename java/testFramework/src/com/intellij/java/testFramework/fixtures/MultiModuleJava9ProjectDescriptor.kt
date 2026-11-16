// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.testFramework.fixtures

import com.intellij.JavaTestUtil
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.LanguageLevelModuleExtension
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.ex.temp.TempFileSystem
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import java.nio.file.Path

/**
 * Compile Dependencies: 'main' -> 'm2', 'main' -> 'm4', 'main' -> 'm5', 'main' -> 'm6' => 'm7', 'm6' -> 'm8'
 * Test dependencies: 'm.test' -> 'm2'
 */
object MultiModuleJava9ProjectDescriptor : DefaultLightProjectDescriptor() {
  enum class ModuleDescriptor(
    @JvmField val moduleName: String,
    val sourceRootName: String? = null,
    internal val testRootName: String? = null,
    internal val resourceRootName: String? = null,
  ) {
    MAIN(TEST_MODULE_NAME, testRootName = "test_src", resourceRootName = "res"),
    M2("light_idea_test_m2", sourceRootName = "src_m2"),
    M3("light_idea_test_m3", sourceRootName = "src_m3"),
    M4("light_idea_test_m4", sourceRootName = "src_m4"),
    M5("light_idea_test_m5", sourceRootName = "src_m5", resourceRootName = "res_m5"),
    M6("light_idea_test_m6", sourceRootName = "src_m6", resourceRootName = "res_m6"),
    M7("light_idea_test_m7", sourceRootName = "src_m7"),
    M8("light_idea_test_m8", sourceRootName = "src_m8"),
    INTERNAL_MAIN("llight_idea_test_internal_main", sourceRootName = "src_internal_main", resourceRootName = "res_internal_main"),
    MR_MAIN("light.idea.test.mr.main", sourceRootName = "src_mr_main"),
    MR_JAVA9("light.idea.test.mr.java9", sourceRootName = "src_mr_java9"),
    M_TEST("light_idea_test_m_test", sourceRootName = "m_src_src", testRootName = "m_test_src");

    fun sourceRoot(): VirtualFile? = if (this === MAIN) LightPlatformTestCase.getSourceRoot() else findRoot(sourceRootName)
    fun testRoot(): VirtualFile? = findRoot(testRootName)
    fun resourceRoot(): VirtualFile? = findRoot(resourceRootName)

    private fun findRoot(rootName: String?): VirtualFile? =
      if (rootName == null) null
      else TempFileSystem.getInstance().findFileByPath("/${rootName}") ?: throw IllegalStateException("Cannot find temp:///${rootName}")
  }

  override fun getSdk(): Sdk = IdeaTestUtil.getMockJdk9()

  override fun setUpProject(project: Project, handler: SetupHandler) {
    super.setUpProject(project, handler)

    runWriteAction {
      val main = ModuleManager.getInstance(project).findModuleByName(TEST_MODULE_NAME)!!
      val modules = createModules { descriptor ->
        val path = "${FileUtil.getTempDirectory()}/${descriptor.moduleName}.iml"
        val module = createModule(project, path)
        ModuleRootModificationUtil.updateModel(module) { configureModule(module, it, descriptor, ::createSourceRoot) }
        module
      }
      configureMainModule(main, modules)
    }
    IndexingTestUtil.waitUntilIndexesAreReady(project)
  }

  /**
   * Applies this descriptor to the main module of a JUnit 5 test, see [lightJava9ModulesProjectFixture].
   *
   * The roots of all modules are the directories next to [sourceRoot] on disk.
   * The fixture calls this method again when it creates the main module again. Then the other modules exist already.
   */
  @TestOnly
  @ApiStatus.Internal
  override fun setUpExistingModule(module: Module, sourceRoot: VirtualFile) {
    checkCanSetUpExistingModule(MultiModuleJava9ProjectDescriptor::class.java)
    val project = module.project
    val baseDir = sourceRoot.parent
    val createRoot: (String) -> VirtualFile = { name -> baseDir.findChild(name) ?: baseDir.createChildDirectory(this, name) }
    ModuleRootModificationUtil.updateModel(module) {
      it.sdk = sdk
      it.addContentEntry(sourceRoot).addSourceFolder(sourceRoot, JavaSourceRootType.SOURCE)
      configureModule(module, it, ModuleDescriptor.MAIN, createRoot)
    }
    val moduleManager = ModuleManager.getInstance(project)
    val modules = if (moduleManager.findModuleByName(ModuleDescriptor.M2.moduleName) == null) {
      createModules { descriptor ->
        val newModule = createModule(project, Path.of(baseDir.path, "${descriptor.moduleName}.iml"))
        ModuleRootModificationUtil.updateModel(newModule) { configureModule(newModule, it, descriptor, createRoot) }
        newModule
      }
    }
    else {
      ModuleDescriptor.entries.filter { it !== ModuleDescriptor.MAIN }.associateWith { moduleManager.findModuleByName(it.moduleName)!! }
    }
    configureMainModule(module, modules)
    IndexingTestUtil.waitUntilIndexesAreReady(project)
  }

  /**
   * Creates the modules of [ModuleDescriptor] except [ModuleDescriptor.MAIN] with [makeModule],
   * and adds their dependencies and libraries.
   */
  private fun createModules(makeModule: (ModuleDescriptor) -> Module): Map<ModuleDescriptor, Module> {
    val modules = ModuleDescriptor.entries.filter { it !== ModuleDescriptor.MAIN }.associateWith(makeModule)
    val m2 = modules.getValue(ModuleDescriptor.M2)
    val m4 = modules.getValue(ModuleDescriptor.M4)
    val m6 = modules.getValue(ModuleDescriptor.M6)
    val m8 = modules.getValue(ModuleDescriptor.M8)
    ModuleRootModificationUtil.addDependency(m6, modules.getValue(ModuleDescriptor.M7), DependencyScope.COMPILE, true)
    ModuleRootModificationUtil.addDependency(m6, m8)
    ModuleRootModificationUtil.addDependency(modules.getValue(ModuleDescriptor.MR_JAVA9), modules.getValue(ModuleDescriptor.MR_MAIN),
                                             DependencyScope.COMPILE, false)
    ModuleRootModificationUtil.addDependency(modules.getValue(ModuleDescriptor.M_TEST), m2, DependencyScope.TEST, false)

    ModuleRootModificationUtil.addModuleLibrary(m2, "${libDir()}/lib-auto-1.0.jar!/")
    ModuleRootModificationUtil.addModuleLibrary(m4, "${libDir()}/lib-auto-2.0.jar!/")
    ModuleRootModificationUtil.addModuleLibrary(m6, "${libDir()}/lib-named-2.0.jar!/")
    ModuleRootModificationUtil.addModuleLibrary(m8, "lib-with-module-info", listOf("${libDir()}/lib-with-module-info.jar!/"),
                                                listOf("${libDir()}/lib-with-module-info-sources.zip!/src"))
    return modules
  }

  /**
   * Adds the dependencies and the libraries of the main module.
   */
  private fun configureMainModule(main: Module, modules: Map<ModuleDescriptor, Module>) {
    for (descriptor in listOf(ModuleDescriptor.M2, ModuleDescriptor.M4, ModuleDescriptor.M5, ModuleDescriptor.M6)) {
      ModuleRootModificationUtil.addDependency(main, modules.getValue(descriptor))
    }

    val libDir = libDir()
    ModuleRootModificationUtil.addModuleLibrary(main, "${libDir}/lib-named-1.0.jar!/")
    ModuleRootModificationUtil.addModuleLibrary(main, "${libDir}/lib-named-2.0.jar!/")
    ModuleRootModificationUtil.addModuleLibrary(main, "${libDir}/lib-with-claimed-name.jar!/")
    ModuleRootModificationUtil.addModuleLibrary(main, "${libDir}/lib-auto-1.0.jar!/")
    ModuleRootModificationUtil.addModuleLibrary(main, "${libDir}/lib-auto-2.0.jar!/")
    ModuleRootModificationUtil.addModuleLibrary(main, "${libDir}/lib-multi-release.jar!/")
    ModuleRootModificationUtil.addModuleLibrary(main, "${libDir}/lib_invalid_1_2.jar!/")
    ModuleRootModificationUtil.addModuleLibrary(main, "${libDir}/lib-xml-bind.jar!/")

    ModuleRootModificationUtil.addModuleLibrary(main, "${libDir}/lib-xml-ws.jar!/")
    ModuleRootModificationUtil.updateModel(main) {
      val entries = it.orderEntries.toMutableList()
      entries.add(0, entries.last())  // places an upgrade module before the JDK
      entries.removeAt(entries.size - 1)
      it.rearrangeOrderEntries(entries.toTypedArray())
    }
  }

  private fun libDir(): String = "jar://${JavaTestUtil.getJavaTestDataPath()}/codeInsight/jigsaw"

  override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) =
    configureModule(module, model, ModuleDescriptor.MAIN, ::createSourceRoot)

  private fun configureModule(module: Module, model: ModifiableRootModel, descriptor: ModuleDescriptor, createRoot: (String) -> VirtualFile) {
    model.getModuleExtension(LanguageLevelModuleExtension::class.java).languageLevel =
      if (descriptor == ModuleDescriptor.MR_MAIN) LanguageLevel.JDK_1_8 else LanguageLevel.JDK_1_9
    if (descriptor !== ModuleDescriptor.MAIN) {
      model.sdk = sdk
    }
    if (descriptor.sourceRootName != null) {
      val sourceRoot = createRoot(descriptor.sourceRootName)
      model.addContentEntry(sourceRoot).addSourceFolder(sourceRoot, JavaSourceRootType.SOURCE)
    }
    if (descriptor.testRootName != null) {
      val testRoot = createRoot(descriptor.testRootName)
      model.addContentEntry(testRoot).addSourceFolder(testRoot, JavaSourceRootType.TEST_SOURCE)
    }
    if (descriptor.resourceRootName != null) {
      val resourceRoot = createRoot(descriptor.resourceRootName)
      model.addContentEntry(resourceRoot).addSourceFolder(resourceRoot, JavaResourceRootType.RESOURCE)
    }
  }

  fun cleanupSourceRoots() = runWriteAction {
    ModuleDescriptor.entries.asSequence()
      .flatMap { sequenceOf(if (it !== ModuleDescriptor.MAIN) it.sourceRoot() else null, it.testRoot(), it.resourceRoot()) }
      .filterNotNull()
      .flatMap { it.children.asSequence() }
      .forEach { it.delete(this) }
  }
}
