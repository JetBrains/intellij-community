// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UseOptimizedEelFunctions")

package com.intellij.execution

import com.intellij.debugger.impl.OutputChecker
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputType
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.ShutDownTracker
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.CompilerTester
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.TestLoggerFactory
import com.intellij.testFramework.common.BazelTestUtil
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.util.Alarm
import com.intellij.util.PathUtil
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import java.util.function.Supplier

/** The JDK that [ExecutionTestCase] uses to compile and to run the test app. */
@Suppress("DEPRECATION", "removal")
@TestOnly
private fun internalJdk(): Sdk = JavaAwareProjectJdkTableImpl.getInstanceEx().internalJdk

/**
 * Creates the output checker of an [ExecutionTestFixture].
 * It takes the same paths as the constructors of [OutputChecker] and of its subclasses.
 * In Java, pass a lambda, for example `(app, output) -> new OutputCheckerWithJunit(app, output)`.
 */
@TestOnly
fun interface OutputCheckerFactory {
  fun create(testAppPath: String, appOutputPath: String): OutputChecker
}

/**
 * The options of [executionTestFixture].
 * Each option replaces a hook of [ExecutionTestCase] and has the default of that hook.
 * A Java test sets them with the fluent `with*` methods.
 */
@TestOnly
class ExecutionTestOptions {
  /** The JDK of the module. It replaces [ExecutionTestCase.getTestProjectJdk]. The default is the internal JDK. */
  var testProjectJdk: Supplier<Sdk> = Supplier { internalJdk() }
    private set

  /** The last argument of [CompilerTester]. It replaces [ExecutionTestCase.overrideCompileJdkAndOutput]. */
  var overrideCompileJdkAndOutput: Boolean = true
    private set

  /**
   * When `true`, a logged error does not fail the test and does not stop [ExecutionTestFixture.waitProcess].
   * It replaces [ExecutionTestCase.areLogErrorsIgnored].
   */
  var areLogErrorsIgnored: Boolean = false
    private set

  /**
   * Runs after the fixture sets the roots, the language level and the compiler output of the main module, and before it sets the JDK.
   * It runs in a write action on the EDT, in the same place as an override of `setUpModule` of [ExecutionTestCase].
   */
  var setUpModule: Consumer<Module> = Consumer { }
    private set

  /**
   * Changes the parameters that [ExecutionTestFixture.createJavaParameters] makes.
   * It replaces an override of [ExecutionTestCase.createJavaParameters] that calls `super` first.
   */
  var updateJavaParameters: Consumer<JavaParameters> = Consumer { }
    private set

  /**
   * The name of the expected output file, and the value of [ExecutionTestFixture.testName].
   * It replaces an override of `getTestName`. When it is `null`, the fixture uses `PlatformTestUtil.getTestName(<method name>, true)`.
   */
  var outputName: String? = null
    private set

  fun withTestProjectJdk(jdk: Supplier<Sdk>): ExecutionTestOptions = apply { testProjectJdk = jdk }

  fun withOverrideCompileJdkAndOutput(value: Boolean): ExecutionTestOptions = apply { overrideCompileJdkAndOutput = value }

  fun withAreLogErrorsIgnored(value: Boolean): ExecutionTestOptions = apply { areLogErrorsIgnored = value }

  fun withSetUpModule(action: Consumer<Module>): ExecutionTestOptions = apply { setUpModule = action }

  fun withUpdateJavaParameters(action: Consumer<JavaParameters>): ExecutionTestOptions = apply { updateJavaParameters = action }

  fun withOutputName(name: String?): ExecutionTestOptions = apply { outputName = name }
}

/**
 * Creates the JUnit 6 replacement of [ExecutionTestCase].
 *
 * Declare it as an instance field of a `@TestApplication` class, because each test needs its own project and test name.
 * The fixture does this setup, in this order:
 * 1. It opens a new project. The project name is the name of the test method, as in JUnit 3. The project language level is 1.6.
 * 2. It creates the main Java module with the same name in the project directory.
 *    The content root is [testAppPath], and the source root is its `src` directory.
 *    The module language level is 1.8, and the compiler output is [ExecutionTestFixture.appOutputPath].
 *    Then it runs [ExecutionTestOptions.setUpModule]. Then it sets the JDK of each module and registers the JDK.
 *    All of this runs in one write action on the EDT, as in the JUnit 3 base.
 * 3. It compiles the project with [CompilerTester] when the output directory of the test app does not exist yet.
 *    The output root is shared by the JVM, so the compile runs once for each test app in the JVM.
 * 4. It calls [OutputChecker.init] with [ExecutionTestFixture.testName].
 *
 * Only the module setup runs on the EDT. The project opening, the compile and the test body run off the EDT, as in the JUnit 3 base.
 *
 * The teardown disposes [ExecutionTestFixture.disposable] and reloads each unsaved document from the disk, because the tests
 * of a JVM share the files of the test app. Then it fails the test when an error was logged during the test,
 * unless [ExecutionTestOptions.areLogErrorsIgnored]. It skips this check when the test already failed. Then the project closes.
 *
 * The fixture does not check the output. Call `checker.checkValid(testProjectJdk)` at the end of the test, as a `runTestRunnable`
 * override of a JUnit 3 subclass did.
 *
 * @param testAppPath the directory of the test app. It holds `src`, and the expected output files in `outs`.
 */
@JvmOverloads
@TestOnly
fun executionTestFixture(
  testAppPath: String,
  outputChecker: OutputCheckerFactory,
  options: ExecutionTestOptions = ExecutionTestOptions(),
): TestFixture<ExecutionTestFixture> = executionTestFixture(testAppPath, outputChecker, options, afterProjectClose = null)

/**
 * The same as the public [executionTestFixture].
 * The framework tears down [afterProjectClose] after the project closes. The debugger fixture checks the javac threads there.
 */
@TestOnly
internal fun executionTestFixture(
  testAppPath: String,
  outputChecker: OutputCheckerFactory,
  options: ExecutionTestOptions,
  afterProjectClose: TestFixture<*>?,
): TestFixture<ExecutionTestFixture> = testFixture("executionTestFixture") { context ->
  val methodName = context.extensionContext.testMethod.orElseThrow {
    IllegalStateException("Declare executionTestFixture as an instance field: it needs the test method")
  }.name
  val name = FileUtil.sanitizeFileName(methodName, false)
  val projectFixture = projectFixture(tempPathFixture(), OpenProjectTask { projectName = name }, openAfterCreation = true)
  if (afterProjectClose != null) {
    projectFixture.dependsOn(afterProjectClose)
  }
  val project = projectFixture.init()
  val disposable = Disposer.newCheckedDisposable("ExecutionTestFixture of $methodName")
  val fixture = ExecutionTestFixture(
    project = project,
    disposable = disposable,
    testName = options.outputName ?: PlatformTestUtil.getTestName(methodName, true),
    testAppPath = testAppPath,
    options = options,
    outputChecker = outputChecker,
  )
  try {
    fixture.setUp(name)
  }
  catch (e: Throwable) {
    Disposer.dispose(disposable)
    throw e
  }
  initialized(fixture) {
    runAll(
      { Disposer.dispose(disposable) },
      { revertUnsavedDocuments(project) },
      {
        if (context.extensionContext.executionException.isEmpty) {
          fixture.checkNoLoggedErrors()
        }
      },
    )
  }
}

/**
 * Reloads each unsaved document from the disk. All tests of a JVM share the files of the test app, and the application keeps
 * an unsaved document after the project closes. So an edit of a test app file must not reach the next test.
 */
private fun revertUnsavedDocuments(project: Project) {
  ApplicationManager.getApplication().invokeAndWait {
    val manager = FileDocumentManager.getInstance()
    for (document in manager.unsavedDocuments) {
      manager.reloadFromDisk(document, project)
    }
  }
}

/**
 * The state and the helpers of a test that [executionTestFixture] creates.
 * The members keep the names of the members of [ExecutionTestCase].
 */
@TestOnly
class ExecutionTestFixture internal constructor(
  /** The project of the test. A new project for each test. */
  val project: Project,
  /** The test root disposable. The fixture disposes it before the project closes. */
  val disposable: Disposable,
  /** The name of the expected output file: `PlatformTestUtil.getTestName(<method name>, true)` or [ExecutionTestOptions.outputName]. */
  val testName: String,
  /** The directory of the test app. */
  val testAppPath: String,
  private val options: ExecutionTestOptions,
  outputChecker: OutputCheckerFactory,
) {
  /** The compiler output directory of the main module. */
  val moduleOutputDir: Path = outputRoot.resolve(PathUtil.getFileName(testAppPath))

  /** The compiler output path of the main module, the class path of [createJavaParameters]. */
  val appOutputPath: String
    get() = moduleOutputDir.toString()

  /** The `data` directory of the test app. */
  val appDataPath: String
    get() = Path.of(testAppPath, "data").toString()

  /** The `config/options` directory of the test app. */
  val appOptionsPath: String
    get() = Path.of(testAppPath, "config", "options").toString()

  /** The checker of the output. [print] and [println] write to it. */
  val checker: OutputChecker = outputChecker.create(testAppPath, appOutputPath)

  /** The time limit of [waitProcess] and [waitFor]. The default is 300 s, as in [ExecutionTestCase]. */
  var timeoutMillis: Int = 300_000

  /** The JDK that the fixture sets on each module. Pass it to [OutputChecker.checkValid]. */
  lateinit var testProjectJdk: Sdk
    private set

  /** The main Java module. Its name is the project name. */
  lateinit var module: Module
    private set

  internal suspend fun setUp(moduleName: String) {
    val jdk = options.testProjectJdk.get()
    testProjectJdk = jdk
    val moduleFile = Path.of(requireNotNull(project.basePath)).resolve("$moduleName.iml")
    edtWriteAction {
      ProjectRootManagerEx.getInstanceEx(project).mergeRootsChangesDuring {
        val newModule = ModuleManager.getInstance(project).newModule(moduleFile, JavaModuleType.getModuleType().id)
        module = newModule
        setUpModuleRoots(newModule)
        IdeaTestUtil.setModuleLanguageLevel(newModule, LanguageLevel.JDK_1_8)
        PsiTestUtil.setCompilerOutputPath(newModule, VfsUtilCore.pathToUrl(moduleOutputDir.toString()), false)
        options.setUpModule.accept(newModule)
        setUpJdk(jdk)
      }
    }
    IdeaTestUtil.setProjectLanguageLevel(project, LanguageLevel.JDK_1_6)
    IndexingTestUtil.suspendUntilIndexesAreReady(project)
    compileProjectOncePerJvm()
    checker.init(testName)
  }

  private fun compileProjectOncePerJvm() {
    if (Files.exists(moduleOutputDir)) {
      return
    }
    Files.createDirectories(moduleOutputDir)
    assertNotNull(VirtualFileManager.getInstance().refreshAndFindFileByNioPath(outputRoot), outputRoot.toString())
    compileProject()
  }

  @RequiresWriteLock
  private fun setUpJdk(jdk: Sdk) {
    val jdkTable = ProjectJdkTable.getInstance()
    if (jdkTable.allJdks.none { it === jdk }) {
      jdkTable.addJdk(jdk, disposable)
    }
    for (each in ModuleManager.getInstance(project).modules) {
      ModuleRootModificationUtil.setModuleSdk(each, jdk)
    }
  }

  private fun compileProject() {
    // CompilerTester adds a JDK that the run uses after the compile, so the fixture disposes it only with the test.
    val compilerTester = CompilerTester(project, ModuleManager.getInstance(project).modules.toList(), disposable,
                                        options.overrideCompileJdkAndOutput)
    for (message in compilerTester.rebuild()) {
      if (message.category == CompilerMessageCategory.ERROR) {
        NioFiles.deleteRecursively(moduleOutputDir)
        fail<Unit>("Compilation failed: $message ${message.virtualFile}")
      }
    }
  }

  internal fun checkNoLoggedErrors() {
    val errorLoggingHappened = TestLoggerFactory.getRethrowErrorNumber()
    if (errorLoggingHappened != 0 && !options.areLogErrorsIgnored) {
      assertEquals(0, errorLoggingHappened, "No ignored errors should happen during execution tests")
    }
  }

  /**
   * Sets the content root of [module] to [testAppPath] and the source root to its `src` directory.
   * Call it in [ExecutionTestOptions.setUpModule] for a second module.
   */
  @RequiresWriteLock
  fun setUpModuleRoots(module: Module) {
    val modulePath = testAppPath
    if (BazelTestUtil.isUnderBazelTest) {
      BazelTestUtil.allowVfsAccessToCanonicalRunfilesRoot(Path.of(modulePath))
    }
    val srcPath = srcPath(modulePath)
    val moduleDir = StandardFileSystems.local().findFileByPath(FileUtil.toSystemIndependentName(modulePath))
    val srcDir = StandardFileSystems.local().findFileByPath(FileUtil.toSystemIndependentName(srcPath))

    val rootManager = ModuleRootManager.getInstance(module)
    PsiTestUtil.removeAllRoots(module, rootManager.sdk)
    PsiTestUtil.addContentRoot(module, requireNotNull(moduleDir) { modulePath })
    PsiTestUtil.addSourceRoot(module, requireNotNull(srcDir) { srcPath })
  }

  /** The source directory of the module in [modulePath]. */
  fun srcPath(modulePath: String): String = Path.of(modulePath, SOURCES_DIRECTORY_NAME).toString()

  /**
   * Makes the parameters that run [mainClass] of the test app with the internal JDK.
   * Then it applies [ExecutionTestOptions.updateJavaParameters].
   */
  fun createJavaParameters(mainClass: String?): JavaParameters {
    val parameters = JavaParameters()
    parameters.classPath.add(appOutputPath)
    parameters.mainClass = mainClass
    parameters.jdk = internalJdk()
    parameters.workingDirectory = testAppPath
    options.updateJavaParameters.accept(parameters)
    return parameters
  }

  /** Adds [msg] as system output to [checker]. */
  fun systemPrintln(@NonNls msg: String) {
    println(msg, ProcessOutputType.SYSTEM)
  }

  fun println(@NonNls s: String, outputType: Key<*>) {
    checker.println(s, outputType)
  }

  fun print(s: String, outputType: Key<*>) {
    checker.print(s, outputType)
  }

  /**
   * Sets a registry value until the end of the test.
   * Use it when the test sets the value in its body. For a constant value, use `@RegistryKey`.
   */
  fun setRegistryPropertyForTest(key: String, value: String) {
    val registryValue = Registry.get(key)
    if (registryValue.isMultiValue) {
      registryValue.selectedOption = value
    }
    else {
      registryValue.setValue(value)
    }
    Disposer.register(disposable) { registryValue.resetToDefault() }
  }

  /**
   * Waits for the end of [processHandler]. It destroys the process after [timeoutMillis].
   * It also destroys the process and fails the test when an error is logged, unless [ExecutionTestOptions.areLogErrorsIgnored].
   */
  fun waitProcess(processHandler: ProcessHandler) {
    val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, disposable)
    val isRunning = AtomicBoolean(true)
    alarm.addRequest({
      if (isRunning.get()) {
        processHandler.destroyProcess()
        LOG.error("process was running over " + timeoutMillis / 1000 + " seconds. Interrupted. ")
      }
    }, timeoutMillis)
    try {
      while (!processHandler.waitFor(100)) {
        ProgressManager.checkCanceled()
        if (!options.areLogErrorsIgnored && TestLoggerFactory.getRethrowErrorNumber() != 0) {
          processHandler.destroyProcess()
          if (!processHandler.waitFor(1_000) && processHandler is KillableProcess && processHandler.canKillProcess()) {
            processHandler.killProcess()
          }
          assertTrue(processHandler.waitFor(10_000), "The process did not stop after a logged error. See the test log.")
          fail<Unit>("An error was logged during process execution. See the test log.")
        }
      }
    }
    finally {
      isRunning.set(false)
      Disposer.dispose(alarm)
    }
  }

  /** Runs [r] on the current thread. It interrupts the thread after [timeoutMillis]. */
  fun waitFor(r: Runnable) {
    val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, disposable)
    val thread = Thread.currentThread()
    val isRunning = AtomicBoolean(true)
    alarm.addRequest({
      if (isRunning.get()) {
        thread.interrupt()
        LOG.error("test was running over " + timeoutMillis / 1000 + " seconds. Interrupted. ")
      }
    }, timeoutMillis)
    r.run()
    isRunning.set(false)
    Thread.interrupted()
    Disposer.dispose(alarm)
  }

  companion object {
    /** The name of the source directory of a test app. */
    const val SOURCES_DIRECTORY_NAME: String = "src"

    private val LOG = Logger.getInstance(ExecutionTestFixture::class.java)

    /** The parent of the output directories of all test apps. It lives until the end of the JVM. */
    private val outputRoot: Path by lazy {
      val root = Files.createTempDirectory("ExecutionTestFixture")
      ShutDownTracker.getInstance().registerShutdownTask { NioFiles.deleteRecursively(root) }
      root
    }
  }
}
