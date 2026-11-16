// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.testFramework.fixtures

import com.intellij.analysis.AnalysisScope
import com.intellij.codeInspection.ex.InspectionToolWrapper
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.MAIN
import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.InspectionTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.createGlobalContextForTool
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.TestOnly
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries

/**
 * The fixture of the tests on the project of [MultiModuleJava9ProjectDescriptor]. Create it with [lightJava9ModulesFixture].
 *
 * The project has the main module `light_idea_test_case` and the other modules of [ModuleDescriptor].
 * Their roots are the directories next to [LightJavaProject.sourceRoot], with the names of [ModuleDescriptor].
 * The members add the files of a test to the roots of a module.
 */
@TestOnly
class LightJava9ModulesFixture internal constructor(
  /**
   * The code insight fixture of the test. Its module is the main module.
   */
  val fixture: LightJavaCodeInsightTestFixture,
  private val lightProject: LightJavaProject,
) {
  val project: Project
    get() = fixture.project

  /**
   * The main module, [ModuleDescriptor.MAIN].
   */
  val module: Module
    get() = fixture.module

  /**
   * Returns the name of the test method without the `test` prefix, as [LightJavaCodeInsightTestFixture.getTestName] does.
   */
  fun getTestName(lowercaseFirstLetter: Boolean): String = fixture.getTestName(lowercaseFirstLetter)

  /**
   * Returns the source root of [module]. It fails when the module has no source root.
   */
  fun sourceRoot(module: ModuleDescriptor): VirtualFile =
    if (module === MAIN) lightProject.sourceRoot else findRoot(module, module.sourceRootName)

  /**
   * Returns the test source root of [module]. It fails when the module has no test source root.
   */
  fun testRoot(module: ModuleDescriptor): VirtualFile = findRoot(module, module.testRootName)

  /**
   * Returns the resource root of [module]. It fails when the module has no resource root.
   */
  fun resourceRoot(module: ModuleDescriptor): VirtualFile = findRoot(module, module.resourceRootName)

  @JvmOverloads
  fun addFile(path: String, text: String, module: ModuleDescriptor = MAIN): VirtualFile =
    VfsTestUtil.createFile(sourceRoot(module), path, text)

  @JvmOverloads
  fun addTestFile(path: String, text: String, module: ModuleDescriptor = MAIN): VirtualFile =
    VfsTestUtil.createFile(testRoot(module), path, text)

  @JvmOverloads
  fun addResourceFile(path: String, text: String, module: ModuleDescriptor = MAIN): VirtualFile =
    VfsTestUtil.createFile(resourceRoot(module), path, text)

  /**
   * Adds the Java files of [classNames] to [module]. The text of each file is `<testDirPath>/<simple name>.java`.
   *
   * @param classNames is like `arrayOf("foo.api.Api", "foo.impl.Impl")`; the file's directory path is created based on FQN
   */
  @JvmOverloads
  fun addJavaFiles(testDirPath: String, classNames: Array<out String>, module: ModuleDescriptor = MAIN) {
    for (className in classNames) {
      val name = className.substringAfterLast('.')
      val sourceFile = FileUtil.findFirstThatExist("$testDirPath/$name.java")
      val text = String(FileUtil.loadFileText(sourceFile!!))
      addFile("${className.replace('.', '/')}.java", text, module)
    }
  }

  @JvmOverloads
  fun moduleInfo(@Language("JAVA") text: String, module: ModuleDescriptor = MAIN): VirtualFile =
    addFile("module-info.java", text, module)

  /**
   * Runs [toolWrapper] on the project, and compares the results with `expected.xml` in [testDirPath]. The tool runs on the EDT.
   */
  fun doGlobalInspectionTest(testDirPath: String, toolWrapper: InspectionToolWrapper<*, *>) {
    val scope = AnalysisScope(project)
    val globalContext = createGlobalContextForTool(scope, project, listOf(toolWrapper))
    runInEdtAndWait {
      InspectionTestUtil.runTool(toolWrapper, scope, globalContext)
      InspectionTestUtil.compareToolResults(globalContext, toolWrapper, true, testDirPath)
    }
  }

  private fun findRoot(module: ModuleDescriptor, rootName: String?): VirtualFile {
    requireNotNull(rootName) { "The module $module has no such root" }
    return checkNotNull(lightProject.sourceRoot.parent.findChild(rootName)) { "No root $rootName of the module $module" }
  }

  /**
   * Deletes the children of the roots that [LightJavaProject] does not clean: the roots of the other modules,
   * and the test and resource roots of the main module. It is the JUnit 5 form of [MultiModuleJava9ProjectDescriptor.cleanupSourceRoots].
   */
  internal suspend fun deleteTestFiles() {
    val baseDir = lightProject.sourceRoot.parent
    val roots = ModuleDescriptor.entries
      .flatMap { listOf(if (it !== MAIN) it.sourceRootName else null, it.testRootName, it.resourceRootName) }
      .mapNotNull { name -> name?.let { baseDir.findChild(it) } }
    withContext(Dispatchers.EDT) {
      WriteCommandAction.runWriteCommandAction(project) {
        for (root in roots) {
          for (child in root.children) {
            child.delete(LightJava9ModulesFixture::class.java)
          }
        }
      }
    }
    // A test can write a file without the VFS. The VFS does not know such a file, so delete it on disk.
    withContext(Dispatchers.IO) {
      for (root in roots) {
        val path = root.toNioPath()
        if (path.exists()) {
          @OptIn(ExperimentalPathApi::class)
          path.listDirectoryEntries().forEach { it.deleteRecursively() }
        }
      }
    }
  }
}

/**
 * Creates the project of [MultiModuleJava9ProjectDescriptor] that the tests of one class share.
 * Declare it in a static field or in a companion object, and give it to [lightJava9ModulesFixture].
 *
 * The project has the main module, the other modules of [ModuleDescriptor], their dependencies and the libraries of the descriptor.
 * When a test changes the main module, the fixture creates the main module again with its dependencies and libraries.
 * The other modules stay for the class, so a test that changes one of them must undo the change.
 */
@TestOnly
fun lightJava9ModulesProjectFixture(): TestFixture<LightJavaProject> = lightJavaProjectFixture(MultiModuleJava9ProjectDescriptor)

/**
 * Creates a [LightJava9ModulesFixture] for one test in the project of [lightJava9ModulesProjectFixture].
 * It is the JUnit 5 form of [LightJava9ModulesCodeInsightFixtureTestCase]. Declare it as an instance field.
 *
 * [testDataPath] is the absolute test data path. When it is `null`, the fixture keeps the default path of [lightJavaCodeInsightFixture].
 *
 * After the test, the fixture deletes the files in all roots of all modules, as the JUnit 3 base does.
 */
@TestOnly
@JvmOverloads
fun lightJava9ModulesFixture(
  projectFixture: TestFixture<LightJavaProject>,
  testDataPath: String? = null,
): TestFixture<LightJava9ModulesFixture> = testFixture("lightJava9ModulesFixture") {
  val lightProject = projectFixture.init()
  check(lightProject.classDescriptor === MultiModuleJava9ProjectDescriptor) { "Create the project with lightJava9ModulesProjectFixture" }
  val fixture = lightJavaCodeInsightFixture(projectFixture).init()
  if (testDataPath != null) {
    fixture.testDataPath = testDataPath
  }
  val java9Fixture = LightJava9ModulesFixture(fixture, lightProject)
  initialized(java9Fixture) {
    java9Fixture.deleteTestFiles()
  }
}
