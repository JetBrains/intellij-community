// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.testFramework.fixtures.TestFixtureBuilder
import com.intellij.testFramework.fixtures.impl.JavaCodeInsightTestFixtureImpl
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path
import java.util.function.BiConsumer
import java.util.function.Consumer

// JUnit 5 checks the virtual file pointers in TestApplicationLeakTrackerExtension after every fixture is torn down.
// The check of the code insight fixture itself runs too early when a module fixture outlives it,
// because the pointers of the module, for example the pointers of a facet, are still alive.
@TestOnly
private class NonTrackingJavaCodeInsightTestFixture(
  project: IdeaProjectTestFixture,
  tempDir: TempDirTestFixture,
) : JavaCodeInsightTestFixtureImpl(project, tempDir) {
  override fun shouldTrackVirtualFilePointers(): Boolean = false
}

/**
 * Creates [JavaCodeInsightTestFixture] fixture that can be used when running JUnit5 tests.
 */
@TestOnly
fun javaCodeInsightFixture(
  projectFixture: TestFixture<Project>,
  tempDirFixture: TestFixture<Path>,
): TestFixture<JavaCodeInsightTestFixture> = codeInsightFixture(projectFixture, tempDirFixture, ::NonTrackingJavaCodeInsightTestFixture)

/**
 * Creates a [JavaCodeInsightTestFixture] with a new heavy project for each test, as `JavaCodeInsightFixtureTestCase` does.
 * Declare the result as an instance field.
 *
 * The fixture builds the project with the fixture builders of [IdeaTestFixtureFactory], and the project has the name of the test method.
 * The project has one Java module. The temp directory is the source content root of the module.
 * When [addSourceRoot] is `false`, the temp directory is only the content root.
 * [tune] gets the module builder before the project opens, as `tuneFixture` of the JUnit 3 base.
 * [testDataPath] is the absolute test data path.
 * After the setup, the project language level is 1.6, as in the JUnit 3 base.
 */
@TestOnly
@JvmOverloads
fun javaModuleCodeInsightFixture(
  testDataPath: String,
  addSourceRoot: Boolean = true,
  tune: Consumer<JavaModuleFixtureBuilder<*>> = Consumer { },
): TestFixture<JavaCodeInsightTestFixture> = builderCodeInsightFixture("javaModuleCodeInsightFixture", testDataPath, { projectBuilder, fixture ->
  val moduleBuilder = projectBuilder.addModule(JavaModuleFixtureBuilder::class.java)
  if (addSourceRoot) {
    moduleBuilder.addSourceContentRoot(fixture.tempDirPath)
  }
  else {
    moduleBuilder.addContentRoot(fixture.tempDirPath)
  }
  tune.accept(moduleBuilder)
}) { fixture ->
  IdeaTestUtil.setProjectLanguageLevel(fixture.project, LanguageLevel.JDK_1_6)
}

/**
 * Creates a [JavaCodeInsightTestFixture] with a new heavy project for each test.
 * It is the JUnit 6 form of a JUnit 3 test that creates the fixture with [JavaTestFixtureFactory.createCodeInsightFixture]
 * on the project of [IdeaTestFixtureFactory.createFixtureBuilder]. Declare the result as an instance field.
 *
 * The project has the name of the test method. [configure] gets the project builder and the code insight fixture before the project opens.
 * It adds the modules with [TestFixtureBuilder.addModule] and tunes them.
 * Unlike [javaModuleCodeInsightFixture], the fixture adds no module and keeps the language level of the project.
 * [testDataPath] is the absolute test data path.
 */
@TestOnly
fun javaProjectBuilderCodeInsightFixture(
  testDataPath: String,
  configure: BiConsumer<TestFixtureBuilder<IdeaProjectTestFixture>, JavaCodeInsightTestFixture>,
): TestFixture<JavaCodeInsightTestFixture> =
  builderCodeInsightFixture("javaProjectBuilderCodeInsightFixture", testDataPath, configure::accept) { }

@TestOnly
private fun builderCodeInsightFixture(
  debugName: String,
  testDataPath: String,
  configure: (TestFixtureBuilder<IdeaProjectTestFixture>, JavaCodeInsightTestFixture) -> Unit,
  afterSetUp: (JavaCodeInsightTestFixture) -> Unit,
): TestFixture<JavaCodeInsightTestFixture> = testFixture(debugName) { context ->
  val projectBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(context.extensionContext.requiredTestMethod.name)
  val fixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.fixture)
  configure(projectBuilder, fixture)
  fixture.testDataPath = testDataPath
  try {
    fixture.setUp()
    afterSetUp(fixture)
  }
  catch (e: Throwable) {
    try {
      fixture.tearDown()
    }
    catch (t: Throwable) {
      e.addSuppressed(t)
    }
    throw e
  }
  initialized(fixture) {
    fixture.tearDown()
  }
}

/**
 * Configures jdk for the project and the module inside JUnit5 test.
 * This method should be called inside method annotated with [org.junit.jupiter.api.BeforeAll].
 *
 * @param level version of the JDK to be set
 */
@TestOnly
@RequiresEdt(generateAssertion = false /* IJPL-115548 */)
fun setUpJdk(level: LanguageLevel, project: Project, module: Module, disposable: Disposable) {
  val jdk = IdeaTestUtil.getMockJdk(level)
  WriteAction.runAndWait<Exception> {
    ProjectJdkTable.getInstance(project).addJdk(jdk, disposable)
    ProjectRootManager.getInstance(project).setProjectSdk(jdk)
    ModuleRootModificationUtil.setModuleSdk(module, jdk)
  }
  IndexingTestUtil.waitUntilIndexesAreReady(project)
}