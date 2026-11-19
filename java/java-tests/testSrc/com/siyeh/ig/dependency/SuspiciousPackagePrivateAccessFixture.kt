// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.dependency

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.java.testFramework.fixtures.JavaModulesProject
import com.intellij.java.testFramework.fixtures.createJavaModule
import com.intellij.java.testFramework.fixtures.deleteChildren
import com.intellij.java.testFramework.fixtures.javaModulesCodeInsightFixture
import com.intellij.java.testFramework.fixtures.registerJavaModulesSdk
import com.intellij.openapi.application.EDT
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.Disposer
import com.intellij.pom.java.LanguageLevel
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.jps.model.java.JavaSourceRootType
import java.nio.file.Path

/**
 * The project of [suspiciousPackagePrivateAccessProjectFixture].
 * [depModule] is the module `dep`. Its source root is [depSourceRoot], and its test source root is [depTestSourceRoot].
 */
class SuspiciousPackagePrivateAccessProject internal constructor(
  project: Project,
  module: Module,
  sourceRoot: Path,
  val depModule: Module,
  val depSourceRoot: Path,
  val depTestSourceRoot: Path,
) : JavaModulesProject(project, module, sourceRoot)

/**
 * Creates the project of the [SuspiciousPackagePrivateAccessInspection] tests for all the tests of a class.
 * Declare it in a static field or in a companion object.
 *
 * The main module `light_idea_test_case` has the `src` source root and depends on the module `dep`.
 * The module `dep` has the `dep-module-src` source root and the `depTests` test source root.
 * Both modules have the highest language level and its Mock JDK. The main module also has the JetBrains annotations with type use.
 */
fun suspiciousPackagePrivateAccessProjectFixture(): TestFixture<SuspiciousPackagePrivateAccessProject> =
  testFixture("suspiciousPackagePrivateAccessProjectFixture") {
    val pathFixture = tempPathFixture()
    val project = projectFixture(pathFixture, openAfterCreation = true).init()
    val root = pathFixture.init()
    val sdk = IdeaTestUtil.getMockJdk(LanguageLevel.HIGHEST.toJavaVersion())
    val sdkDisposable = Disposer.newDisposable("suspiciousPackagePrivateAccessProjectFixture SDK")
    registerJavaModulesSdk(sdk, sdkDisposable)
    val sourceRoot = root.resolve("src")
    val main = createJavaModule(project, root.resolve(LightProjectDescriptor.TEST_MODULE_NAME + ".iml"), sdk, LanguageLevel.HIGHEST,
                                listOf(sourceRoot to JavaSourceRootType.SOURCE))
    ModuleRootModificationUtil.updateModel(main, DefaultLightProjectDescriptor::addJetBrainsAnnotationsWithTypeUse)
    val depSourceRoot = root.resolve("dep-module-src")
    val depTestSourceRoot = root.resolve("depTests")
    val depModule = createJavaModule(project, root.resolve("dep-module").resolve("dep.iml"), sdk, LanguageLevel.HIGHEST,
                                     listOf(depSourceRoot to JavaSourceRootType.SOURCE, depTestSourceRoot to JavaSourceRootType.TEST_SOURCE))
    ModuleRootModificationUtil.addDependency(main, depModule)
    IndexingTestUtil.suspendUntilIndexesAreReady(project)
    initialized(SuspiciousPackagePrivateAccessProject(project, main, sourceRoot, depModule, depSourceRoot, depTestSourceRoot)) {
      withContext(Dispatchers.EDT) { Disposer.dispose(sdkDisposable) }
    }
  }

/**
 * The fixture of one [SuspiciousPackagePrivateAccessInspection] test. Create it with [suspiciousPackagePrivateAccessFixture].
 */
class SuspiciousPackagePrivateAccessFixture internal constructor(
  /**
   * The code insight fixture of the test.
   */
  val fixture: JavaCodeInsightTestFixture,
  private val extension: String,
  private val testMethodName: String,
) {
  /**
   * Configures `src/<test name>.<extension>` of the test data and checks the highlighting.
   */
  fun doTestWithDependency() {
    fixture.configureByFile("src/" + PlatformTestUtil.getTestName(testMethodName, false) + "." + extension)
    fixture.testHighlighting(true, false, false)
  }
}

/**
 * Creates the fixture of one [SuspiciousPackagePrivateAccessInspection] test in the project of [projectFixture].
 * Declare it as an instance field.
 *
 * Before the test, the fixture enables the inspection, as `LightJavaInspectionTestCase.setUp` does.
 * Then it copies the `dep` directory of [testDataPath] to the source root of the module `dep`.
 * [extension] is the extension of the test files, for example `java`.
 * After the test, the fixture deletes the files of both source roots of the module `dep`.
 */
fun suspiciousPackagePrivateAccessFixture(
  projectFixture: TestFixture<SuspiciousPackagePrivateAccessProject>,
  extension: String,
  testDataPath: String,
): TestFixture<SuspiciousPackagePrivateAccessFixture> = testFixture("suspiciousPackagePrivateAccessFixture") { context ->
  val modulesProject = projectFixture.init()
  val fixture = javaModulesCodeInsightFixture(projectFixture, testDataPath).init()
  val project = fixture.project
  val inspection = SuspiciousPackagePrivateAccessInspection()
  fixture.enableInspections(inspection)
  val displayKey = checkNotNull(HighlightDisplayKey.find(inspection.shortName))
  val currentProfile = ProjectInspectionProfileManager.getInstance(project).currentProfile
  val errorLevel = currentProfile.getErrorLevel(displayKey, null)
  if (errorLevel == HighlightDisplayLevel.DO_NOT_SHOW || errorLevel == HighlightDisplayLevel.WEAK_WARNING) {
    currentProfile.setErrorLevel(displayKey, HighlightDisplayLevel.WARNING, project)
  }
  fixture.copyDirectoryToProject("dep", "../dep-module-src")
  initialized(SuspiciousPackagePrivateAccessFixture(fixture, extension, context.extensionContext.requiredTestMethod.name)) {
    deleteChildren(project, modulesProject.depSourceRoot)
    deleteChildren(project, modulesProject.depTestSourceRoot)
  }
}
