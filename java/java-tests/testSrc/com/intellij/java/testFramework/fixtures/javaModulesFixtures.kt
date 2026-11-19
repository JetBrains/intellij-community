// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.testFramework.fixtures

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.LanguageLevelModuleExtension
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.ExactNameTempDirTestFixture
import com.intellij.project.TestProjectManager
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.TestApplicationManager
import com.intellij.testFramework.TestDataProvider
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.testFramework.fixtures.impl.JavaCodeInsightTestFixtureImpl
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import java.nio.file.Path
import java.util.function.Function
import java.util.function.Supplier
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively

/**
 * The project of a test that creates its own modules.
 * [module] is the module of [javaModulesCodeInsightFixture]. The test creates its files in [sourceRoot].
 */
open class JavaModulesProject(val project: Project, val module: Module, val sourceRoot: Path)

/**
 * The project that [multiModuleProjectFixture] creates from [descriptor].
 */
class MultiModuleProject internal constructor(
  val descriptor: MultiModuleProjectDescriptor,
  project: Project,
  module: Module,
  sourceRoot: Path,
) : JavaModulesProject(project, module, sourceRoot)

/**
 * Creates the project of a [MultiModuleProjectDescriptor] for one test. Declare it as an instance field.
 *
 * [descriptor] gets the name of the test method and returns the descriptor of the test.
 * The fixture creates the project in a temporary directory and registers the SDK of the descriptor.
 * Then [MultiModuleProjectDescriptor.setUpProject] copies the `before` directory to [MultiModuleProjectDescriptor.getProjectPath]
 * and loads its modules. After that, the fixture opens the project, as a JUnit 3 light test does.
 * The project directory is not the directory of the modules.
 * [JavaModulesProject.module] is the main module of the descriptor, and [JavaModulesProject.sourceRoot] is its last source root.
 * After the test, the fixture closes the project and deletes [MultiModuleProjectDescriptor.getProjectPath].
 */
fun multiModuleProjectFixture(
  descriptor: Function<String, MultiModuleProjectDescriptor>,
): TestFixture<MultiModuleProject> = testFixture("multiModuleProjectFixture") { context ->
  val multiModuleDescriptor = descriptor.apply(context.extensionContext.requiredTestMethod.name)
  val projectPath = multiModuleDescriptor.projectPath
  testFixture("multiModuleProjectPath") {
    initialized(projectPath) {
      withContext(Dispatchers.IO) {
        @OptIn(ExperimentalPathApi::class)
        projectPath.deleteRecursively()
      }
    }
  }.init()
  val project = projectFixture(tempPathFixture()).init()
  val sdkDisposable = Disposer.newDisposable("multiModuleProjectFixture SDK")
  var mainModule: Module? = null
  var mainSourceRoot: VirtualFile? = null
  try {
    withContext(Dispatchers.EDT) {
      multiModuleDescriptor.registerSdk(sdkDisposable)
      multiModuleDescriptor.setUpProject(project, object : LightProjectDescriptor.SetupHandler {
        override fun moduleCreated(module: Module) {
          mainModule = module
        }

        override fun sourceRootCreated(sourceRoot: VirtualFile) {
          mainSourceRoot = sourceRoot
        }
      })
      check((ProjectManagerEx.getInstanceEx() as TestProjectManager).openProject(project)) { "The project did not open" }
    }
    IndexingTestUtil.suspendUntilIndexesAreReady(project)
  }
  catch (e: Throwable) {
    withContext(Dispatchers.EDT) { Disposer.dispose(sdkDisposable) }
    throw e
  }
  val module = checkNotNull(mainModule) { "The descriptor created no main module" }
  val sourceRoot = checkNotNull(mainSourceRoot) { "The main module has no source root" }
  initialized(MultiModuleProject(multiModuleDescriptor, project, module, sourceRoot.toNioPath())) {
    withContext(Dispatchers.EDT) { Disposer.dispose(sdkDisposable) }
  }
}

/**
 * Creates the project of a [MultiModuleProjectDescriptor] for one test, as the other `multiModuleProjectFixture` does.
 * Use this form when the descriptor does not depend on the name of the test method.
 */
fun multiModuleProjectFixture(
  descriptor: Supplier<MultiModuleProjectDescriptor>,
): TestFixture<MultiModuleProject> = multiModuleProjectFixture { _: String -> descriptor.get() }

/**
 * Registers [sdk] in the SDK table until [parentDisposable] is disposed, as a JUnit 3 light test does with the SDK of its descriptor.
 */
suspend fun registerJavaModulesSdk(sdk: Sdk, parentDisposable: Disposable) {
  edtWriteAction {
    ProjectJdkTable.getInstance().addJdk(sdk, parentDisposable)
  }
}

/**
 * Creates the Java module of [moduleFile] in [project], as `LightProjectDescriptor.createModule` does.
 * The module gets [sdk] and [languageLevel], and a content root and a source root for each entry of [sourceRoots].
 * The function creates the directory of each source root.
 */
suspend fun createJavaModule(
  project: Project,
  moduleFile: Path,
  sdk: Sdk,
  languageLevel: LanguageLevel,
  sourceRoots: List<Pair<Path, JpsModuleSourceRootType<*>>> = emptyList(),
): Module {
  val roots = withContext(Dispatchers.IO) {
    sourceRoots.map { (path, type) ->
      val directory = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path.createDirectories())) {
        "No source root: $path"
      }
      directory to type
    }
  }
  val module = edtWriteAction {
    ModuleManager.getInstance(project).newModule(moduleFile, JavaModuleType.getModuleType().id)
  }
  ModuleRootModificationUtil.updateModel(module) { model ->
    model.sdk = sdk
    for ((directory, type) in roots) {
      model.addContentEntry(directory).addSourceFolder(directory, type)
    }
    model.getModuleExtension(LanguageLevelModuleExtension::class.java).languageLevel = languageLevel
  }
  IndexingTestUtil.suspendUntilIndexesAreReady(project)
  return module
}

/**
 * Creates a [JavaCodeInsightTestFixture] for one test on [JavaModulesProject.module]. Declare it as an instance field.
 *
 * The test creates its files in [JavaModulesProject.sourceRoot] with their exact names.
 * The fixture sets the project language level to [LanguageLevel.JDK_1_6], as `LightJavaCodeInsightFixtureTestCase.setUp` does.
 * After the test, it deletes the files in [JavaModulesProject.sourceRoot], as the light temporary directory of a JUnit 3 test does.
 */
fun javaModulesCodeInsightFixture(
  projectFixture: TestFixture<JavaModulesProject>,
  testDataPath: String,
): TestFixture<JavaCodeInsightTestFixture> = testFixture("javaModulesCodeInsightFixture") {
  val modulesProject = projectFixture.init()
  val project = modulesProject.project
  val ideaProjectFixture = object : IdeaProjectTestFixture {
    private val testRootDisposable = Disposer.newDisposable("javaModulesCodeInsightFixture project fixture")

    override fun getProject(): Project = project

    override fun getModule(): Module = modulesProject.module

    override fun getTestRootDisposable(): Disposable = testRootDisposable

    override fun setUp() {
      TestApplicationManager.getInstance().setDataProvider(TestDataProvider(project))
    }

    override fun tearDown() {
      try {
        Disposer.dispose(testRootDisposable)
      }
      finally {
        TestApplicationManager.getInstance().setDataProvider(null)
      }
    }
  }
  val codeInsightFixture = NonTrackingJavaModulesCodeInsightTestFixture(ideaProjectFixture,
                                                                        ExactNameTempDirTestFixture(modulesProject.sourceRoot))
  codeInsightFixture.testDataPath = testDataPath
  codeInsightFixture.setUp()
  withContext(Dispatchers.EDT) {
    IdeaTestUtil.setProjectLanguageLevel(project, LanguageLevel.JDK_1_6)
  }
  initialized(codeInsightFixture) {
    try {
      codeInsightFixture.tearDown()
    }
    finally {
      deleteChildren(project, modulesProject.sourceRoot)
    }
  }
}

/**
 * Deletes the files and the directories in [directory] through the VFS.
 */
suspend fun deleteChildren(project: Project, directory: Path) {
  val root = withContext(Dispatchers.IO) {
    LocalFileSystem.getInstance().refreshAndFindFileByNioFile(directory)
  } ?: return
  withContext(Dispatchers.EDT) {
    WriteCommandAction.runWriteCommandAction(project) {
      for (child in root.children) {
        child.delete(JavaModulesProject::class.java)
      }
    }
  }
}

/**
 * Does not check the virtual file pointers in [tearDown]. The JUnit 5 leak check after the test checks them.
 */
private class NonTrackingJavaModulesCodeInsightTestFixture(
  projectFixture: IdeaProjectTestFixture,
  tempDir: TempDirTestFixture,
) : JavaCodeInsightTestFixtureImpl(projectFixture, tempDir) {
  override fun shouldTrackVirtualFilePointers(): Boolean = false
}
