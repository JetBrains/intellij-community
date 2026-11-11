// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.fixtures

import com.intellij.application.options.CodeStyle
import com.intellij.facet.FacetManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ExportableOrderEntry
import com.intellij.openapi.roots.LanguageLevelModuleExtension
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.ExactNameTempDirTestFixture
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.codeStyle.CodeStyleSchemes
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageManagerImpl
import com.intellij.testFramework.CodeStyleSettingsTracker
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestApplicationManager
import com.intellij.testFramework.TestDataProvider
import com.intellij.testFramework.closeLegacyLightProject
import com.intellij.testFramework.fixtures.impl.JavaCodeInsightTestFixtureImpl
import com.intellij.testFramework.common.checkEditorsReleased
import com.intellij.testFramework.common.runAllSuspend
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.util.application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path
import java.util.function.Function
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries

/**
 * The project and the module that the tests of one class share. It is the JUnit 5 form of the light project of
 * [LightJavaCodeInsightFixtureTestCase].
 *
 * Create it with [lightJavaProjectFixture]. Give it to [lightJavaCodeInsightFixture].
 */
@TestOnly
class LightJavaProject internal constructor(
  val project: Project,
  /**
   * The `src` directory on disk. It is the content root and the source root of [module].
   * The tests create their files in it, and the fixture deletes them after each test.
   */
  val sourceRoot: VirtualFile,
  internal val sourceRootPath: Path,
  private val moduleFile: Path,
  internal val classDescriptor: LightProjectDescriptor,
) {
  /**
   * The module `light_idea_test_case`. The fixture creates a new module when a test changes it, so do not keep it between tests.
   */
  lateinit var module: Module
    private set

  private var moduleDescriptor: LightProjectDescriptor? = null
  private var moduleSnapshot: List<String> = emptyList()
  private var testSdkDisposable: Disposable? = null

  internal suspend fun createModule(descriptor: LightProjectDescriptor, sdkParentDisposable: Disposable?) {
    if (sdkParentDisposable != null) {
      withContext(Dispatchers.EDT) {
        descriptor.registerSdk(sdkParentDisposable)
      }
    }
    val module = edtWriteAction {
      // An earlier module of this class can leave its file. A new module with the same file would load its settings.
      moduleFile.deleteIfExists()
      ModuleManager.getInstance(project).newModule(moduleFile, descriptor.moduleTypeId)
    }
    edtWriteAction {
      descriptor.setUpExistingModule(module, sourceRoot)
    }
    IndexingTestUtil.suspendUntilIndexesAreReady(project)
    this.module = module
    moduleDescriptor = descriptor
    moduleSnapshot = readAction { snapshot(module) }
  }

  private suspend fun disposeModule() {
    edtWriteAction {
      if (!module.isDisposed) {
        ModuleManager.getInstance(project).disposeModule(module)
      }
    }
  }

  /**
   * Gives the test a module of [descriptor].
   * When [descriptor] is not equal to the descriptor of the module, the fixture creates a new module and registers the SDK of [descriptor].
   */
  internal suspend fun setUpForTest(descriptor: LightProjectDescriptor) {
    if (descriptor == moduleDescriptor && !module.isDisposed) {
      return
    }
    disposeModule()
    val sdkDisposable = Disposer.newDisposable("lightJavaCodeInsightFixture test SDK")
    testSdkDisposable = sdkDisposable
    createModule(descriptor, sdkDisposable)
  }

  /**
   * Restores the class state after a test.
   * When the test used another descriptor, or changed the module, the fixture creates a new module of [classDescriptor].
   * It does this before the leak checks of the test, so the SDK of another descriptor does not stay.
   */
  internal suspend fun tearDownForTest() {
    withContext(Dispatchers.EDT) {
      if (LanguageLevelProjectExtension.getInstance(project).languageLevel != PROJECT_LANGUAGE_LEVEL) {
        IdeaTestUtil.setProjectLanguageLevel(project, PROJECT_LANGUAGE_LEVEL)
      }
    }
    val changed = module.isDisposed || moduleDescriptor != classDescriptor || readAction { snapshot(module) } != moduleSnapshot
    if (changed) {
      disposeModule()
      createModule(classDescriptor, null)
    }
    testSdkDisposable?.let {
      testSdkDisposable = null
      withContext(Dispatchers.EDT) {
        Disposer.dispose(it)
      }
    }
  }

  internal suspend fun deleteTestFiles() {
    withContext(Dispatchers.EDT) {
      WriteCommandAction.runWriteCommandAction(project) {
        for (child in sourceRoot.children) {
          child.delete(LightJavaProject::class.java)
        }
      }
    }
    // A test can write a file without the VFS. The VFS does not know such a file, so delete it on disk.
    withContext(Dispatchers.IO) {
      @OptIn(kotlin.io.path.ExperimentalPathApi::class)
      sourceRootPath.listDirectoryEntries().forEach { it.deleteRecursively() }
    }
  }
}

/**
 * The [JavaCodeInsightTestFixture] of one test of [lightJavaCodeInsightFixture]. It also knows the name of the test.
 */
@TestOnly
interface LightJavaCodeInsightTestFixture : JavaCodeInsightTestFixture {
  /**
   * Returns the name of the test method without the `test` prefix, as `UsefulTestCase.getTestName` does.
   * With [lowercaseFirstLetter], the first letter becomes lower case, unless the name is all in upper case.
   */
  fun getTestName(lowercaseFirstLetter: Boolean): String
}

@TestOnly
private class LightJavaCodeInsightTestFixtureImpl(
  projectFixture: IdeaProjectTestFixture,
  tempDir: TempDirTestFixture,
  private val testMethodName: String,
) : JavaCodeInsightTestFixtureImpl(projectFixture, tempDir), LightJavaCodeInsightTestFixture {
  // The module of the class lives longer than this fixture, so the pointer check of the fixture runs too early.
  // The JUnit 5 leak check after the test checks the pointers.
  override fun shouldTrackVirtualFilePointers(): Boolean = false

  override fun getTestName(lowercaseFirstLetter: Boolean): String = PlatformTestUtil.getTestName(testMethodName, lowercaseFirstLetter)
}

// LightJavaCodeInsightFixtureTestCase.setUp sets this project language level for each test.
private val PROJECT_LANGUAGE_LEVEL: LanguageLevel = LanguageLevel.JDK_1_6

/**
 * The parts of the module that the fixture compares after each test: the name, the SDK, the language level,
 * the order entries with the library roots, the content roots with their folders, and the facets.
 */
private fun snapshot(module: Module): List<String> {
  val rootManager = ModuleRootManager.getInstance(module)
  return buildList {
    add("name=${module.name}")
    add("sdk=${rootManager.sdk?.name}")
    add("level=${rootManager.getModuleExtension(LanguageLevelModuleExtension::class.java)?.languageLevel}")
    for (entry in rootManager.orderEntries) {
      val scope = (entry as? ExportableOrderEntry)?.let { "${it.scope}:${it.isExported}" }
      add("entry=${entry.javaClass.simpleName}:${entry.presentableName}:$scope")
      if (entry is LibraryOrderEntry) {
        for (type in OrderRootType.getAllTypes()) {
          entry.getRootUrls(type).mapTo(this) { "root=${type.name()}:$it" }
        }
      }
    }
    for (contentEntry in rootManager.contentEntries) {
      add("content=${contentEntry.url}")
      contentEntry.sourceFolders.mapTo(this) { "source=${it.url}:${it.rootType}:${it.packagePrefix}" }
      contentEntry.excludeFolderUrls.mapTo(this) { "exclude=$it" }
    }
    FacetManager.getInstance(module).allFacets.mapTo(this) { "facet=${it.typeId}:${it.name}" }
  }
}

/**
 * Creates the project and the module that the tests of one class share, as the light project of
 * [LightJavaCodeInsightFixtureTestCase] does. Declare it in a static field or in a companion object.
 *
 * The fixture opens a project, sets the project language level to [LanguageLevel.JDK_1_6], and registers the SDK of [descriptor].
 * It creates the module `light_idea_test_case` with the `src` directory as its content root and source root.
 * Then it applies [descriptor] to the module with [LightProjectDescriptor.setUpExistingModule].
 * A descriptor that overrides [LightProjectDescriptor.setUpProject] or [LightProjectDescriptor.createDirForSources] fails there.
 *
 * Each class gets its own project. JUnit 3 kept one project for all classes with an equal descriptor.
 */
@TestOnly
@JvmOverloads
fun lightJavaProjectFixture(
  descriptor: LightProjectDescriptor = LightJavaCodeInsightFixtureTestCase.JAVA_LATEST,
): TestFixture<LightJavaProject> = testFixture("lightJavaProjectFixture") {
  val pathFixture = tempPathFixture()
  val project = projectFixture(pathFixture, openAfterCreation = true).init()
  val path = pathFixture.init()
  val sourceRootPath = withContext(Dispatchers.IO) { path.resolve("src").createDirectories() }
  val sourceRoot = withContext(Dispatchers.IO) {
    requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(sourceRootPath)) { "No source root: $sourceRootPath" }
  }
  val classDisposable = Disposer.newDisposable("lightJavaProjectFixture")
  val lightProject = LightJavaProject(project, sourceRoot, sourceRootPath,
                                      path.resolve(LightProjectDescriptor.TEST_MODULE_NAME + ".iml"), descriptor)
  try {
    withContext(Dispatchers.EDT) {
      IdeaTestUtil.setProjectLanguageLevel(project, PROJECT_LANGUAGE_LEVEL)
    }
    lightProject.createModule(descriptor, classDisposable)
  }
  catch (e: Throwable) {
    withContext(Dispatchers.EDT) { Disposer.dispose(classDisposable) }
    throw e
  }
  initialized(lightProject) {
    runAllSuspend(
      { closeLegacyLightProject() },
      { withContext(Dispatchers.EDT) { Disposer.dispose(classDisposable) } },
    )
  }
}

/**
 * Creates a [LightJavaCodeInsightTestFixture] for one test in the project of [projectFixture].
 * It is the JUnit 5 form of [LightJavaCodeInsightFixtureTestCase]. Declare it as an instance field.
 *
 * The module has the descriptor of [lightJavaProjectFixture].
 * The test data path is the community path and [basePath], as [LightJavaCodeInsightFixtureTestCase.getTestDataPath] computes it.
 * The fixture does not read [com.intellij.testFramework.TestDataPath].
 *
 * The test creates its files in [LightJavaProject.sourceRoot] with their exact names.
 * After the test, the fixture does what a JUnit 3 light test does:
 * - it deletes the files of the test and cleans the project with [TestApplicationManager.cleanUpProjectForNextTest];
 * - it checks the code style settings, the editors, and the injectors;
 * - it sets the project language level back to [LanguageLevel.JDK_1_6];
 * - it creates a new module when the test changed the module, for example with a new library or a new language level.
 *
 * Without the EDT, put a PSI read in a read action. JUnit 3 ran each test on the EDT.
 */
@TestOnly
@JvmOverloads
fun lightJavaCodeInsightFixture(
  projectFixture: TestFixture<LightJavaProject>,
  basePath: String = "",
): TestFixture<LightJavaCodeInsightTestFixture> = lightJavaCodeInsightFixture(projectFixture, null, basePath)

/**
 * Creates a [LightJavaCodeInsightTestFixture] as the other overload does, for a class that picks the descriptor for each test.
 *
 * [descriptor] gets the name of the test method without the `test` prefix, as `getTestName(false)` of JUnit 3 returns it.
 * It returns the descriptor of the test.
 * When it is not equal to the descriptor of [lightJavaProjectFixture], the test gets a new module of this descriptor.
 * After the test, the class module comes back. A descriptor that is not equal for each call costs a new module for each test,
 * as in JUnit 3. Keep such a descriptor in a constant.
 */
@TestOnly
@JvmOverloads
fun lightJavaCodeInsightFixture(
  projectFixture: TestFixture<LightJavaProject>,
  descriptor: Function<String, LightProjectDescriptor>?,
  basePath: String = "",
): TestFixture<LightJavaCodeInsightTestFixture> = testFixture("lightJavaCodeInsightFixture") { context ->
  val lightProject = projectFixture.init()
  val project = lightProject.project
  val testMethodName = context.extensionContext.requiredTestMethod.name
  val testDescriptor = descriptor?.apply(PlatformTestUtil.getTestName(testMethodName, false)) ?: lightProject.classDescriptor
  lightProject.setUpForTest(testDescriptor)
  InjectedLanguageManagerImpl.pushInjectors(project)

  val ideaProjectFixture = object : IdeaProjectTestFixture {
    override fun getProject(): Project = project

    override fun getModule(): Module = lightProject.module

    override fun setUp() {
      TestApplicationManager.getInstance().setDataProvider(TestDataProvider(project))
    }

    override fun tearDown() {
      TestApplicationManager.getInstance().setDataProvider(null)
    }
  }
  val codeInsightFixture = LightJavaCodeInsightTestFixtureImpl(ideaProjectFixture, ExactNameTempDirTestFixture(lightProject.sourceRootPath),
                                                                testMethodName)
  codeInsightFixture.testDataPath = lightTestDataPath(basePath)
  codeInsightFixture.setUp()

  project.messageBus.connect(codeInsightFixture.testRootDisposable).subscribe(ModuleListener.TOPIC, object : ModuleListener {
    override fun modulesAdded(project: Project, modules: List<Module>) {
      throw AssertionError("Adding modules is not permitted in light tests: " + modules.joinToString { it.name })
    }
  })
  val codeStyleTracker = CodeStyleSettingsTracker {
    if (CodeStyleSchemes.getInstance().currentScheme == null) CodeStyle.createTestSettings() else CodeStyle.getSettings(project)
  }

  initialized(codeInsightFixture) {
    runAllSuspend(
      { codeInsightFixture.tearDown() },
      { lightProject.deleteTestFiles() },
      { TestApplicationManager.cleanUpProjectForNextTest(project) },
      { codeStyleTracker.checkForSettingsDamage() },
      {
        withContext(Dispatchers.EDT) {
          application.checkEditorsReleased(project)
        }
      },
      { InjectedLanguageManagerImpl.checkInjectorsAreDisposed(project) },
      { lightProject.tearDownForTest() },
    )
  }
}

// The test data path of LightJavaCodeInsightFixtureTestCase.getTestDataPath.
internal fun lightTestDataPath(basePath: String): String {
  val communityPath = PlatformTestUtil.getCommunityPath().replace('\\', '/')
  val path = communityPath + basePath
  return if (Path.of(path).exists()) path else "$communityPath/../$basePath"
}
