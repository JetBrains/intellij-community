// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "DestructuringDeclaration")

package com.intellij.platform.buildScripts.testFramework.distributionContent

import com.intellij.diagnostic.ThreadDumper
import com.intellij.diagnostic.dumpCoroutines
import com.intellij.diagnostic.enableCoroutineDump
import com.intellij.openapi.util.io.NioFiles
import com.intellij.platform.buildScripts.testFramework.createBuildOptionsForTest
import com.intellij.platform.buildScripts.testFramework.customizeBuildOptionsForPackagingContentTest
import com.intellij.platform.buildScripts.testFramework.doRunTestBuild
import com.intellij.platform.runtime.repository.RuntimeModuleId
import com.intellij.platform.runtime.repository.RuntimeModuleRepository
import com.intellij.testFramework.TestLoggerFactory
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.TracerProvider
import io.opentelemetry.context.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildPaths
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.ProprietaryBuildTools
import org.jetbrains.intellij.build.impl.DistributionBuilderState
import org.jetbrains.intellij.build.impl.moduleRepository.MODULE_DESCRIPTORS_COMPACT_PATH
import org.jetbrains.intellij.build.impl.SUPPORTED_DISTRIBUTIONS
import org.jetbrains.intellij.build.impl.asArchivedIfNeeded
import org.jetbrains.intellij.build.impl.buildDistributions
import org.jetbrains.intellij.build.impl.createBuildContext
import org.jetbrains.intellij.build.impl.createCompilationContext
import org.jetbrains.intellij.build.impl.getOsAndArchSpecificDistDirectory
import org.jetbrains.intellij.build.impl.logging.BuildMessagesImpl
import org.jetbrains.intellij.build.impl.toBazelIfNeeded
import org.jetbrains.intellij.build.telemetry.JaegerJsonSpanExporterManager
import org.jetbrains.intellij.build.telemetry.TraceManager
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import org.jetbrains.jps.model.JpsProject
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.TestInstance
import org.opentest4j.MultipleFailuresError
import org.opentest4j.TestAbortedException
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds

internal data class PackageResult(
  @JvmField val projectHome: Path,
  @JvmField val jpsProject: JpsProject,
  @JvmField val content: ParsedContentReport,
  @JvmField val runtimeModuleRepository: RuntimeModuleRepository?,
)

private data class PackagingSuiteTelemetry(
  @JvmField val traceFile: Path,
  @JvmField val rootSpan: Span,
  @JvmField val parentContext: Context,
)

private data class TaskResult<T>(
  @JvmField val value: T? = null,
  @JvmField val failure: Throwable? = null,
)

private data class ValidationTask(
  @JvmField val spec: PackagingSuiteValidationSpec,
  @JvmField val resultDeferred: Deferred<TaskResult<List<PackagingCheckFailure>>>,
)

private data class TargetValidationTask(
  @JvmField val spec: PackagingTargetValidationSpec,
  @JvmField val packagingTask: PackagingTask,
  @JvmField val resultDeferred: Deferred<TaskResult<List<PackagingCheckFailure>>>,
)

private data class PackagingTask(
  @JvmField val spec: PackagingTargetSpec,
  @JvmField val startSignal: CompletableDeferred<Unit>?,
  /**
   * The layout of the target, which the build computes before it packs a jar.
   *
   * A packaging task that fails before it computes the layout completes this exceptionally, so a `LAYOUT` validation
   * aborts instead of waiting for a result that never comes.
   */
  @JvmField val layoutDeferred: CompletableDeferred<PackagedLayout>,
  @JvmField val resultDeferred: Deferred<TaskResult<PackageResult>>,
) {
  fun start() {
    val startSignal = startSignal
    if (startSignal == null) {
      resultDeferred.start()
    }
    else {
      startSignal.complete(Unit)
    }
  }
}

private data class PluginCheckTask(
  @JvmField val packagingTask: PackagingTask,
  @JvmField val resultDeferred: Deferred<TaskResult<List<PackagingCheckFailure>>>,
)

private inline fun <T> Iterable<T>.startAllDeferreds(getDeferred: (T) -> Deferred<*>?) {
  for (item in this) {
    getDeferred(item)?.start()
  }
}

private fun Iterable<PackagingTask>.startAllPackagingTasks() {
  for (task in this) {
    task.start()
  }
}

typealias PackagingSuiteValidator = suspend (context: PackagingSuiteContext) -> List<PackagingCheckFailure>
typealias PackagingTargetValidator = suspend (context: PackagingTargetValidationContext) -> List<PackagingCheckFailure>

@Internal
enum class PackagingSuiteTaskScheduling {
  LAZY_BY_FACTORY,
  FULL_SUITE_OPTIMIZED,
}

@Internal
data class PackagingSuiteContext(
  @JvmField val projectHome: Path,
  @JvmField val tempDir: Path,
  @JvmField val compilationContext: CompilationContext,
) {
  val project: JpsProject
    get() = compilationContext.project
}

@Internal
data class PackagingSuiteValidationSpec(
  @JvmField val name: String,
  @JvmField val problemMessage: String,
  @JvmField val threshold: Int = 50,
  @JvmField val isBlocking: Boolean = false,
  @JvmField val alwaysCreateSuccessTest: Boolean = false,
  @JvmField val skipIfAborted: Boolean = true,
  @JvmField val validator: PackagingSuiteValidator,
)

/**
 * The layout of one target, as the build computed it before it packed a jar.
 *
 * A validation that reads it states the content of the distribution from the project model. It therefore runs beside
 * the packaging of its target, and not after it.
 */
@Internal
class PackagedLayout(
  @JvmField val buildContext: BuildContext,
  @JvmField val distributionState: DistributionBuilderState,
)

/**
 * What a target validation reads, which decides when it can run.
 *
 * [LAYOUT] waits for the layout alone, so it overlaps the packaging of its own target. [CONTENT] waits for the
 * packaged content report, so it runs after the packaging of its target ends.
 */
@Internal
enum class PackagingTargetValidationStage {
  LAYOUT,
  CONTENT,
}

@Internal
class PackagingTargetValidationContext internal constructor(
  @JvmField val target: PackagingTargetSpec,
  @JvmField val projectHome: Path,
  @JvmField val tempDir: Path,
  @JvmField val project: JpsProject,
  @JvmField val outputProvider: ModuleOutputProvider,
  @JvmField val layout: PackagedLayout,
  private val packageResultProvider: suspend () -> PackageResult,
) {
  /** The content report of the packaged distribution. A [PackagingTargetValidationStage.LAYOUT] validation must not read it. */
  suspend fun content(): ParsedContentReport = packageResultProvider().content

  /** The runtime module repository of the packaged distribution, or `null` when the build generated none. */
  suspend fun runtimeModuleRepository(): RuntimeModuleRepository? = packageResultProvider().runtimeModuleRepository
}

@Internal
data class PackagingTargetValidationSpec(
  @JvmField val targetId: String,
  @JvmField val name: String,
  @JvmField val problemMessage: String,
  @JvmField val threshold: Int = Int.MAX_VALUE,
  @JvmField val alwaysCreateSuccessTest: Boolean = true,
  @JvmField val stage: PackagingTargetValidationStage = PackagingTargetValidationStage.CONTENT,
  @JvmField val validator: PackagingTargetValidator,
)

@Internal
data class PackagingTargetSpec(
  @JvmField val id: String,
  @JvmField val createProductProperties: (projectHome: Path) -> ProductProperties,
  @JvmField val contentYamlPath: String?,
  @JvmField val buildTools: ProprietaryBuildTools = ProprietaryBuildTools.DUMMY,
  @JvmField val checkPlugins: Boolean = true,
  @JvmField val suggestedReviewer: String? = null,
) {
  override fun toString(): String = id
}

@Internal
data class PackagingSuiteSpec(
  @JvmField val name: String,
  @JvmField val homePath: Path,
  @JvmField val targets: List<PackagingTargetSpec>,
  @JvmField val validations: List<PackagingSuiteValidationSpec> = emptyList(),
  @JvmField val targetValidations: List<PackagingTargetValidationSpec> = emptyList(),
  @JvmField val taskScheduling: PackagingSuiteTaskScheduling = PackagingSuiteTaskScheduling.LAZY_BY_FACTORY,
)

@Internal
data class PackagingSuiteTraceSettings(
  @JvmField val enabled: Boolean,
  @JvmField val traceFile: Path?,
)

private const val PACKAGING_SUITE_TELEMETRY_ENABLED_PROPERTY = "intellij.build.test.packaging.telemetry.enabled"
private const val PACKAGING_SUITE_TRACE_FILE_PROPERTY = "intellij.build.test.packaging.trace.file"
private val packagingSuiteNoopTracer = TracerProvider.noop().get("packaging-suite")

@Internal
class PackagingSuiteFixture private constructor(
  private val spec: PackagingSuiteSpec,
  private val scopeJob: Job,
  private val diagnostics: PackagingSuiteHangDiagnostics,
  private val tempDir: Path,
  private val telemetry: PackagingSuiteTelemetry?,
  private val tracerOverride: AutoCloseable?,
  private val suiteContextDeferred: Deferred<PackagingSuiteContext>,
  private val validationTasks: List<ValidationTask>,
  private val packagingTasks: List<PackagingTask>,
  private val pluginCheckTasks: List<PluginCheckTask>,
  private val targetValidationTasks: List<TargetValidationTask>,
) : AutoCloseable {
  /** The report of the first frozen wait. It stays null while the fixture works. */
  @Volatile
  private var hangReport: String? = null

  companion object {
    fun create(spec: PackagingSuiteSpec): PackagingSuiteFixture {
      require(spec.targets.isNotEmpty()) { "Packaging suite must contain at least one target" }
      ensureUniqueNames(kind = "target", names = spec.targets.map { it.id })
      ensureUniqueNames(kind = "validation", names = spec.validations.map { it.name })
      ensureUniqueNames(kind = "target validation", names = spec.targetValidations.map { "${it.targetId}:${it.name}" })
      ensureTargetValidationsReferenceExistingTargets(spec)

      return createSharedFixture(spec)
    }

    private fun createSharedFixture(spec: PackagingSuiteSpec): PackagingSuiteFixture {
      installCoroutineDebugProbes()
      val traceSettings = resolvePackagingSuiteTraceSettings(spec)
      val telemetry = createSuiteTelemetry(spec = spec, traceSettings = traceSettings)
      val tracerOverride = traceSettings.takeUnless { it.enabled }?.let { TraceManager.pushTracer(packagingSuiteNoopTracer) }

      val scopeJob = SupervisorJob()
      val diagnostics = PackagingSuiteHangDiagnostics()
      val scope = createPackagingSuiteScope(job = scopeJob, diagnostics = diagnostics)
      var tempDirForCleanup: Path? = null
      try {
        val tempDir = Files.createTempDirectory("${spec.name}-packaging-suite-").also { tempDirForCleanup = it }
        val suiteContextDeferred = scope.async(start = CoroutineStart.LAZY) {
          withTelemetrySpan(telemetry = telemetry, name = "create shared compilation context") {
            PackagingSuiteContext(
              projectHome = spec.homePath,
              tempDir = tempDir,
              compilationContext = createSharedCompilationContext(projectHome = spec.homePath, tempDir = tempDir, scope = scope),
            )
          }
        }
        // The gate compiles nothing. Under Bazel `compileModules` has an empty body, and a JPS run reuses the
        // project output, so the call resolves the project dependencies and marks the output as available. The
        // suite does it once, and every derived context shares the result through `JpsCompilationData`.
        val moduleOutputDeferred = scope.async(start = CoroutineStart.LAZY) {
          withTelemetrySpan(
            telemetry = telemetry,
            name = "prepare shared module output",
            configure = { span ->
              span.setAttribute("packaging.target.count", spec.targets.size.toLong())
            },
          ) {
            suiteContextDeferred.await().compilationContext.compileProductionModules()
          }
        }

        val optimizedFullSuiteScheduling = spec.taskScheduling == PackagingSuiteTaskScheduling.FULL_SUITE_OPTIMIZED
        val validationTasks = createValidationTasks(
          scope = scope,
          spec = spec,
          suiteContextDeferred = suiteContextDeferred,
          moduleOutputDeferred = moduleOutputDeferred,
          telemetry = telemetry,
        )
        val packagingTasks = createPackagingTasks(
          scope = scope,
          spec = spec,
          suiteContextDeferred = suiteContextDeferred,
          moduleOutputDeferred = moduleOutputDeferred,
          validationTasks = validationTasks,
          telemetry = telemetry,
          waitForScheduledStart = optimizedFullSuiteScheduling,
        )
        val pluginCheckTasks = createPluginCheckTasks(scope = scope, packagingTasks = packagingTasks, telemetry = telemetry)
        val targetValidationTasks = createTargetValidationTasks(
          scope = scope,
          spec = spec,
          suiteContextDeferred = suiteContextDeferred,
          packagingTasks = packagingTasks,
          telemetry = telemetry,
        )
        if (optimizedFullSuiteScheduling) {
          scheduleFullSuiteWork(
            scope = scope,
            validationTasks = validationTasks,
            packagingTasks = packagingTasks,
            pluginCheckTasks = pluginCheckTasks,
            targetValidationTasks = targetValidationTasks,
          )
        }

        return PackagingSuiteFixture(
          spec = spec,
          scopeJob = scopeJob,
          diagnostics = diagnostics,
          tempDir = tempDir,
          telemetry = telemetry,
          tracerOverride = tracerOverride,
          suiteContextDeferred = suiteContextDeferred,
          validationTasks = validationTasks,
          packagingTasks = packagingTasks,
          pluginCheckTasks = pluginCheckTasks,
          targetValidationTasks = targetValidationTasks,
        )
      }
      catch (t: Throwable) {
        runCatching { runBlocking { scopeJob.cancelAndJoin() } }
        runCatching { tracerOverride?.close() }
        telemetry?.rootSpan?.end()
        runCatching { runBlocking { TraceManager.flush() } }
        tempDirForCleanup?.also(NioFiles::deleteRecursively)
        throw t
      }
    }
  }

  fun createSuiteValidationTests(): List<DynamicTest> {
    if (validationTasks.isEmpty()) {
      return listOf(DynamicTest.dynamicTest("no suite validations") {})
    }

    validationTasks.startAllDeferreds { it.resultDeferred }

    val result = ArrayList<DynamicTest>()
    for (task in validationTasks) {
      val taskResult = awaitTask("suite validation '${task.spec.name}'") { task.resultDeferred.await() }
      val failure = taskResult.failure
      if (failure != null) {
        if (failure is TestAbortedException && task.spec.skipIfAborted) {
          continue
        }
        result.add(DynamicTest.dynamicTest(task.spec.name) { throw failure })
        continue
      }

      result.addAll(
        createDynamicTests(
          failures = taskResult.value.orEmpty(),
          problemMessage = task.spec.problemMessage,
          threshold = task.spec.threshold,
          successTestName = task.spec.name.takeIf { task.spec.alwaysCreateSuccessTest },
        )
      )
    }
    return result
  }

  fun createBuildTests(): List<DynamicTest> {
    if (!isOptimizedFullSuiteScheduling()) {
      startBlockingValidationTasks()
      packagingTasks.startAllPackagingTasks()
    }

    val tests = ArrayList<DynamicTest>(packagingTasks.size)
    for (task in packagingTasks) {
      tests.add(DynamicTest.dynamicTest(task.spec.id) {
        awaitTask("packaging of '${task.spec.id}'") {
          task.resultDeferred.await().getOrThrow()
        }
      })
    }
    return tests
  }

  fun createPlatformTests(): List<DynamicTest> {
    val tasksWithContentChecks = packagingTasks.filter { it.spec.contentYamlPath != null }
    if (!isOptimizedFullSuiteScheduling()) {
      startBlockingValidationTasks()
      tasksWithContentChecks.startAllPackagingTasks()
    }

    val tests = ArrayList<DynamicTest>(tasksWithContentChecks.size)
    for (task in tasksWithContentChecks) {
      val expectedContentYamlPath = requireNotNull(task.spec.contentYamlPath)
      tests.add(DynamicTest.dynamicTest(task.spec.id) {
        awaitTask("platform content check of '${task.spec.id}'") {
          withTelemetrySpan(
            telemetry = telemetry,
            name = "platform content check: ${task.spec.id}",
            configure = { span ->
              span.setAttribute("packaging.target.id", task.spec.id)
            },
          ) {
            val packageResult = task.resultDeferred.await().getOrAbort("Platform content check for ${task.spec.id} skipped because packaging failed")
            checkThatContentIsNotChanged(
              actualFileEntries = packageResult.content.platform,
              expectedFile = spec.homePath.resolve(expectedContentYamlPath),
              projectHome = packageResult.projectHome,
              isBundled = true,
              suggestedReviewer = task.spec.suggestedReviewer,
            )
          }
        }
      })
    }
    return tests
  }

  fun createPluginTests(): List<DynamicTest> {
    if (!isOptimizedFullSuiteScheduling()) {
      startBlockingValidationTasks()
      packagingTasks.filter { it.spec.checkPlugins }.startAllPackagingTasks()
    }
    pluginCheckTasks.startAllDeferreds { it.resultDeferred }

    val tests = ArrayList<DynamicTest>(packagingTasks.size)
    val resolvedCheckResults = awaitTask("plugin content checks") {
      pluginCheckTasks.map { it.resultDeferred }.awaitAll()
    }
    for ((task, checkResult) in pluginCheckTasks.zip(resolvedCheckResults)) {
      val packagingTask = task.packagingTask
      tests.addAll(
        createPluginContentDynamicTests(
          targetId = packagingTask.spec.id,
          checkPlugins = packagingTask.spec.checkPlugins,
          failures = checkResult.value.orEmpty(),
          failure = checkResult.failure,
        )
      )
    }
    return tests
  }

  fun createTargetValidationTests(): List<DynamicTest> {
    if (targetValidationTasks.isEmpty()) {
      return listOf(DynamicTest.dynamicTest("no target validations") {})
    }

    startBlockingValidationTasks()
    if (!isOptimizedFullSuiteScheduling()) {
      targetValidationTasks.mapTo(LinkedHashSet()) { it.packagingTask }.startAllPackagingTasks()
    }
    targetValidationTasks.startAllDeferreds { it.resultDeferred }

    val tests = ArrayList<DynamicTest>()
    for (task in targetValidationTasks) {
      val testName = "${task.spec.targetId} ${task.spec.name}"
      val taskResult = awaitTask("target validation '$testName'") { task.resultDeferred.await() }
      val failure = taskResult.failure
      if (failure != null) {
        tests.add(DynamicTest.dynamicTest(testName) { throw failure })
        continue
      }

      tests.addAll(
        createDynamicTests(
          failures = taskResult.value.orEmpty().map { it.copy(name = "$testName: ${it.name}") },
          problemMessage = "${task.spec.problemMessage} for ${task.spec.targetId}",
          threshold = task.spec.threshold,
          successTestName = testName.takeIf { task.spec.alwaysCreateSuccessTest },
        )
      )
    }
    return tests
  }

  private fun startBlockingValidationTasks() {
    startBlockingValidationTasks(validationTasks)
  }

  private fun isOptimizedFullSuiteScheduling(): Boolean = spec.taskScheduling == PackagingSuiteTaskScheduling.FULL_SUITE_OPTIMIZED

  /**
   * Waits for [block], and fails at once when an earlier wait found the fixture scope frozen.
   *
   * A frozen scope cannot finish any later task, so every remaining test factory would wait its own [HANG_DUMP_DELAY].
   */
  private fun <T> awaitTask(what: String, block: suspend () -> T): T {
    hangReport?.let {
      throw PackagingSuiteHangException("Packaging suite: $what cannot run, because the fixture scope is frozen. The first report:\n$it")
    }
    return awaitOnTestThread(what = what, diagnostics = diagnostics, report = { hangReport = it }, block = block)
  }

  override fun close() {
    val hangReport = hangReport
    if (hangReport == null) {
      awaitTask("cancellation of the fixture scope") { scopeJob.cancelAndJoin() }
    }
    else {
      // the scope cannot run a coroutine any more, so the join, the build messages and the trace flush would never end
      scopeJob.cancel()
      System.err.println("Packaging suite: the fixture scope is frozen, so the close skips the trace and the build messages.")
    }

    try {
      if (hangReport == null) {
        if (suiteContextDeferred.isCompleted) {
          runCatching { runBlocking { suiteContextDeferred.await().compilationContext.messages.close() } }
        }
        telemetry?.let {
          it.rootSpan.end()
          runCatching { runBlocking { TraceManager.flush() } }
          println("Packaging suite trace is written to ${it.traceFile}")
        }
      }
      NioFiles.deleteRecursively(tempDir)
    }
    finally {
      runCatching { tracerOverride?.close() }
    }
  }
}

private fun startBlockingValidationTasks(validationTasks: List<ValidationTask>) {
  for (task in validationTasks) {
    if (task.spec.isBlocking) {
      task.resultDeferred.start()
    }
  }
}

private val HANG_DUMP_DELAY = 10.minutes
private val HANG_PROBE_INTERVAL = 1.minutes
/** The CPU time that a frozen JVM can still use in one probe interval. */
private val HANG_IDLE_CPU_FLOOR = 200.milliseconds
private const val HANG_IDLE_CPU_FRACTION = 0.05
private const val SCHEDULER_WORKER_THREAD_PREFIX = "DefaultDispatcher-worker"
private const val SCHEDULER_WORKER_CLASS = $$"kotlinx.coroutines.scheduling.CoroutineScheduler$Worker"
private const val RUNNING_COROUTINE_MARKER = "state: RUNNING"

/**
 * Installs the coroutine debug probes, so that [dumpCoroutines] sees the coroutines of the fixture.
 *
 * Call it before the fixture scope creates a coroutine. The probes ignore a coroutine that was created before the install.
 * A test JVM loads the no-op `DebugProbesKt` of the Kotlin stdlib, so the install redefines that class with ByteBuddy.
 * This module therefore has `byte-buddy` and `byte-buddy-agent` on its runtime classpath. Without them the install
 * reports success and captures nothing.
 */
internal fun installCoroutineDebugProbes() {
  enableCoroutineDump().onFailure {
    System.err.println("Packaging suite: cannot install the coroutine debug probes, so a hang dump will be empty: $it")
  }
}

/** A wait on the fixture scope stopped to make progress. */
internal class PackagingSuiteHangException(message: String) : IllegalStateException(message)

/**
 * Keeps the evidence for a hang report, and catches an exception that the coroutine machinery loses.
 *
 * `DispatchedTask.run` gives every throwable of a resume to `handleCoroutineException`, which asks the
 * [CoroutineExceptionHandler] of the context first. Without the handler such an exception reaches
 * `UnhandledCoroutineExceptionHandlerService`, which has an empty body, and then the handler of the test framework,
 * which prints only when the test ends. A hang never ends, so the exception stays invisible, and the coroutine that
 * the resume belongs to stays suspended forever.
 */
internal class PackagingSuiteHangDiagnostics {
  private val escapedExceptions = CopyOnWriteArrayList<String>()

  val exceptionHandler: CoroutineExceptionHandler = CoroutineExceptionHandler { context, e ->
    val name = context[CoroutineName]?.name ?: "an unnamed coroutine"
    val text = "Packaging suite: an exception escaped the coroutine machinery in $name:\n${e.stackTraceToString()}"
    escapedExceptions.add(text)
    System.err.println(text)
  }

  fun describeEscapedExceptions(): String = escapedExceptions.joinToString(separator = "\n").ifEmpty { "none" }
}

/**
 * Creates a scope that reports an exception of the coroutine machinery to [diagnostics].
 *
 * The fixture and its test use it, so both report such an exception the same way.
 */
internal fun createPackagingSuiteScope(job: Job, diagnostics: PackagingSuiteHangDiagnostics): CoroutineScope {
  @Suppress("RAW_SCOPE_CREATION")
  return CoroutineScope(job + Dispatchers.Default + diagnostics.exceptionHandler)
}

/**
 * Runs [block] on the test thread and waits for it.
 *
 * A test factory waits here for work on the fixture scope. A suspension that never resumes is invisible in a thread dump,
 * because every dispatcher thread is idle. So after [dumpDelay] a watchdog looks for a freeze every [probeInterval].
 * A slow suite keeps waiting, and a frozen one fails with a report that names the suspension. The fixture calls
 * [installCoroutineDebugProbes] when it is created.
 */
internal fun <T> awaitOnTestThread(
  what: String,
  diagnostics: PackagingSuiteHangDiagnostics = PackagingSuiteHangDiagnostics(),
  dumpDelay: Duration = HANG_DUMP_DELAY,
  probeInterval: Duration = HANG_PROBE_INTERVAL,
  report: (String) -> Unit = System.err::println,
  block: suspend () -> T,
): T {
  return runBlocking {
    val hang = CompletableDeferred<Nothing>()
    val watchdog = PackagingSuiteHangWatchdog(
      what = what,
      diagnostics = diagnostics,
      dumpDelay = dumpDelay,
      probeInterval = probeInterval,
      report = report,
      hang = hang,
    )
    watchdog.start()
    val task = async { block() }
    try {
      select {
        task.onAwait { it }
        hang.onAwait { it }
      }
    }
    finally {
      watchdog.stop()
    }
  }
}

/**
 * Watches one wait of [awaitOnTestThread] for a freeze.
 *
 * It runs on a plain thread, and not on a coroutine, for two reasons. A frozen dispatcher cannot stop it. It also puts
 * no coroutine of its own into the dump that it compares.
 */
private class PackagingSuiteHangWatchdog(
  private val what: String,
  private val diagnostics: PackagingSuiteHangDiagnostics,
  private val dumpDelay: Duration,
  private val probeInterval: Duration,
  private val report: (String) -> Unit,
  private val hang: CompletableDeferred<Nothing>,
) {
  private val startNanos = System.nanoTime()

  @Volatile
  private var stopped = false

  private val thread = thread(start = false, isDaemon = true, name = "packaging suite hang watchdog") { watch() }

  fun start() {
    thread.start()
  }

  fun stop() {
    stopped = true
    thread.interrupt()
  }

  private fun watch() {
    try {
      Thread.sleep(dumpDelay.inWholeMilliseconds)
      var previous = takeHangSample()
      var lastProgressNanos = 0L
      while (!stopped) {
        Thread.sleep(probeInterval.inWholeMilliseconds)
        val current = takeHangSample()
        if (stopped) {
          return
        }
        if (isFrozen(previous = previous, current = current, probeInterval = probeInterval)) {
          val text = describeHang(what = what, elapsed = elapsed(), probeInterval = probeInterval, diagnostics = diagnostics, sample = current)
          report(text)
          hang.completeExceptionally(PackagingSuiteHangException(text))
          return
        }

        val now = System.nanoTime()
        if (lastProgressNanos == 0L || (now - lastProgressNanos).nanoseconds >= dumpDelay) {
          lastProgressNanos = now
          System.err.println("Packaging suite: $what is still running after ${elapsed()}, and the fixture scope makes progress.")
        }
        previous = current
      }
    }
    catch (_: InterruptedException) {
    }
  }

  private fun elapsed(): Duration = (System.nanoTime() - startNanos).nanoseconds.inWholeMilliseconds.milliseconds
}

private class HangSample(
  val javaThreadCpuTime: Duration?,
  @JvmField val schedulerWorkersAreParked: Boolean,
  @JvmField val coroutineDump: String,
)

private fun takeHangSample(): HangSample {
  return HangSample(
    javaThreadCpuTime = javaThreadCpuTime(),
    schedulerWorkersAreParked = schedulerWorkersAreParked(),
    coroutineDump = dumpCoroutines() ?: "the coroutine debug probes are not installed",
  )
}

/**
 * Tells whether the scope is frozen and not slow.
 *
 * A frozen scope has an unchanged coroutine dump, no coroutine that runs, a parked worker for every worker of the
 * scheduler, and almost no CPU time. A slow suite fails at least one of these tests, because it runs a coroutine, or it
 * starts one, or it uses the CPU on some thread.
 *
 * One state looks frozen and is not. A scope where every coroutine waits for a timer, and no thread works, gives the
 * same picture. The suite has no such wait, and the watchdog looks only after [HANG_DUMP_DELAY].
 */
private fun isFrozen(previous: HangSample, current: HangSample, probeInterval: Duration): Boolean {
  if (previous.coroutineDump != current.coroutineDump) {
    return false
  }
  if (current.coroutineDump.contains(RUNNING_COROUTINE_MARKER)) {
    return false
  }
  if (!previous.schedulerWorkersAreParked || !current.schedulerWorkersAreParked) {
    return false
  }

  val previousCpu = previous.javaThreadCpuTime ?: return true
  val currentCpu = current.javaThreadCpuTime ?: return true
  val used = currentCpu - previousCpu
  return used >= Duration.ZERO && used <= maxOf(HANG_IDLE_CPU_FLOOR, probeInterval * HANG_IDLE_CPU_FRACTION)
}

/**
 * Sums the CPU time of every Java thread except the caller.
 *
 * The garbage collector and the JIT compiler have no Java thread, so their CPU time does not count. The result is null
 * when the JVM does not measure the CPU time of a thread.
 */
private fun javaThreadCpuTime(): Duration? {
  val threads = ManagementFactory.getThreadMXBean()
  if (!threads.isThreadCpuTimeSupported || !threads.isThreadCpuTimeEnabled) {
    return null
  }

  val self = Thread.currentThread().threadId()
  var total = 0L
  for (id in threads.allThreadIds) {
    if (id == self) {
      continue
    }
    val cpuTime = threads.getThreadCpuTime(id)
    if (cpuTime > 0) {
      total += cpuTime
    }
  }
  return total.nanoseconds
}

private fun schedulerWorkersAreParked(): Boolean {
  return ManagementFactory.getThreadMXBean().dumpAllThreads(false, false)
    .asSequence()
    .filter { it.threadName.startsWith(SCHEDULER_WORKER_THREAD_PREFIX) }
    .all { info ->
      info.stackTrace.any { it.className == SCHEDULER_WORKER_CLASS && (it.methodName == "park" || it.methodName == "tryPark") }
    }
}

private fun describeHang(
  what: String,
  elapsed: Duration,
  probeInterval: Duration,
  diagnostics: PackagingSuiteHangDiagnostics,
  sample: HangSample,
): String {
  return buildString {
    appendLine("Packaging suite: $what made no progress for $probeInterval after $elapsed, so the wait aborts.")
    appendLine("The coroutine dump did not change, no coroutine runs, and every worker of the scheduler is parked.")
    appendLine("Dispatchers.Default: ${describeDefaultDispatcher()}")
    appendLine("Exceptions that escaped the coroutine machinery: ${diagnostics.describeEscapedExceptions()}")
    appendLine("Coroutine dump:")
    appendLine(sample.coroutineDump)
    appendLine("Thread dump:")
    append(ThreadDumper.dumpThreadsToString())
  }
}

private fun describeDefaultDispatcher(): String {
  // `Dispatchers.Default.toString()` is a constant. The scheduler behind it prints the worker states, the queue sizes
  // and the CPU permits that it gave out.
  return runCatching { (Dispatchers.Default as ExecutorCoroutineDispatcher).executor.toString() }
    .getOrElse { "cannot read the state of the scheduler: $it" }
}

private fun scheduleFullSuiteWork(
  scope: CoroutineScope,
  validationTasks: List<ValidationTask>,
  packagingTasks: List<PackagingTask>,
  pluginCheckTasks: List<PluginCheckTask>,
  targetValidationTasks: List<TargetValidationTask>,
) {
  // Every validation awaits the shared module output on its own, and no validation awaits another one.
  validationTasks.startAllDeferreds { it.resultDeferred }
  val targetValidationPackagingTasks = targetValidationTasks.mapTo(LinkedHashSet()) { it.packagingTask }
  targetValidationPackagingTasks.startAllPackagingTasks()
  targetValidationTasks.startAllDeferreds { it.resultDeferred }
  pluginCheckTasks.startAllDeferreds { task ->
    if (task.packagingTask in targetValidationPackagingTasks && task.packagingTask.spec.checkPlugins) task.resultDeferred else null
  }
  val remainingPackagingTasks = packagingTasks.filter { it !in targetValidationPackagingTasks }
  val pluginCheckTasksByPackagingTask = pluginCheckTasks.associateBy { it.packagingTask }

  scope.launch(Dispatchers.Default) {
    startRemainingTasksWithRollingReplenishment(
      startedTasks = targetValidationPackagingTasks,
      remainingTasks = remainingPackagingTasks,
      getCompletion = { it.resultDeferred },
      startTask = { packagingTask ->
        packagingTask.start()
        if (packagingTask.spec.checkPlugins) {
          pluginCheckTasksByPackagingTask.get(packagingTask)?.resultDeferred?.start()
        }
      },
    )
  }
}

internal suspend fun <T> startRemainingTasksWithRollingReplenishment(
  startedTasks: Collection<T>,
  remainingTasks: Collection<T>,
  getCompletion: (T) -> Deferred<*>,
  startTask: (T) -> Unit,
) {
  val maxParallelTasks = maxOf(startedTasks.size, remainingTasks.size)
  if (maxParallelTasks == 0) {
    return
  }

  val activeTasks = LinkedHashSet<T>(maxParallelTasks)
  activeTasks.addAll(startedTasks)
  val tasksToStart = ArrayDeque<T>(remainingTasks.size)
  tasksToStart.addAll(remainingTasks)

  fun fillAvailableSlots() {
    while (activeTasks.size < maxParallelTasks && tasksToStart.isNotEmpty()) {
      val task = tasksToStart.removeFirst()
      activeTasks.add(task)
      startTask(task)
    }
  }

  if (activeTasks.isEmpty()) {
    fillAvailableSlots()
    return
  }

  while (tasksToStart.isNotEmpty()) {
    val completedTask = select {
      for (task in activeTasks) {
        getCompletion(task).onAwait { task }
      }
    }
    activeTasks.remove(completedTask)
    fillAvailableSlots()
  }
}

@Internal
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class PackagingSuiteTestBase {
  protected abstract val packagingFixture: PackagingSuiteFixture

  @TestFactory
  fun suiteValidations(): List<DynamicTest> = packagingFixture.createSuiteValidationTests()

  @TestFactory
  fun build(): List<DynamicTest> = packagingFixture.createBuildTests()

  @TestFactory
  fun platform(): List<DynamicTest> = packagingFixture.createPlatformTests()

  @TestFactory
  fun plugins(): List<DynamicTest> = packagingFixture.createPluginTests()

  @TestFactory
  fun targetValidations(): List<DynamicTest> = packagingFixture.createTargetValidationTests()
}

private fun createValidationTasks(
  scope: CoroutineScope,
  spec: PackagingSuiteSpec,
  suiteContextDeferred: Deferred<PackagingSuiteContext>,
  moduleOutputDeferred: Deferred<Unit>,
  telemetry: PackagingSuiteTelemetry?,
): List<ValidationTask> {
  return spec.validations.map { validation ->
    ValidationTask(
      spec = validation,
      resultDeferred = scope.async(start = CoroutineStart.LAZY) {
        captureTaskResult {
          withTelemetrySpan(
            telemetry = telemetry,
            name = "suite validation: ${validation.name}",
            configure = { span ->
              span.setAttribute("packaging.validation.name", validation.name)
            },
          ) {
            moduleOutputDeferred.await()
            validation.validator(suiteContextDeferred.await())
          }
        }
      },
    )
  }
}

private fun createPackagingTasks(
  scope: CoroutineScope,
  spec: PackagingSuiteSpec,
  suiteContextDeferred: Deferred<PackagingSuiteContext>,
  moduleOutputDeferred: Deferred<Unit>,
  validationTasks: List<ValidationTask>,
  telemetry: PackagingSuiteTelemetry?,
  waitForScheduledStart: Boolean,
): List<PackagingTask> {
  val blockingTasks = validationTasks.filter { it.spec.isBlocking }
  val result = ArrayList<PackagingTask>(spec.targets.size)
  for (target in spec.targets) {
    val startSignal = if (waitForScheduledStart) CompletableDeferred<Unit>() else null
    val coroutineStart = if (waitForScheduledStart) CoroutineStart.DEFAULT else CoroutineStart.LAZY
    val layoutDeferred = CompletableDeferred<PackagedLayout>()
    result.add(
      PackagingTask(
        spec = target,
        startSignal = startSignal,
        layoutDeferred = layoutDeferred,
        resultDeferred = scope.async(start = coroutineStart) {
          try {
            startSignal?.await()
            val taskResult = captureTaskResult {
              withTelemetrySpan(
                telemetry = telemetry,
                name = "package target: ${target.id}",
                configure = { span ->
                  span.setAttribute("packaging.target.id", target.id)
                },
              ) {
                ensureBlockingValidationsSucceededOrAbort(blockingTasks)
                moduleOutputDeferred.await()
                val suiteContext = suiteContextDeferred.await()
                val context = createDerivedBuildContext(
                  sharedCompilationContext = suiteContext.compilationContext,
                  target = target,
                  projectHome = spec.homePath,
                  buildOutputRoot = suiteContext.tempDir.resolve(target.id),
                )
                computePackageResult(context = context, layoutDeferred = layoutDeferred)
              }
            }
            // the task can fail before it computes the layout, and a LAYOUT validation waits for the layout alone.
            // `completeExceptionally` does nothing when the layout is there already.
            taskResult.failure?.let { layoutDeferred.completeExceptionally(it) }
            taskResult
          }
          finally {
            // `captureTaskResult` rethrows a cancellation, so a cancelled task reaches no line above that completes the
            // layout. A LAYOUT validation must abort then, not wait for a layout that never comes.
            if (!layoutDeferred.isCompleted) {
              layoutDeferred.completeExceptionally(IllegalStateException("Packaging of '${target.id}' was cancelled before it computed the layout"))
            }
          }
        },
      )
    )
  }
  return result
}

private fun createPluginCheckTasks(
  scope: CoroutineScope,
  packagingTasks: List<PackagingTask>,
  telemetry: PackagingSuiteTelemetry?,
): List<PluginCheckTask> {
  return packagingTasks.map { task ->
    PluginCheckTask(
      packagingTask = task,
      resultDeferred = scope.async(Dispatchers.Default, start = CoroutineStart.LAZY) {
        if (!task.spec.checkPlugins) {
          return@async TaskResult(value = emptyList())
        }

        captureTaskResult {
          withTelemetrySpan(
            telemetry = telemetry,
            name = "plugin content check: ${task.spec.id}",
            configure = { span ->
              span.setAttribute("packaging.target.id", task.spec.id)
            },
          ) {
            val packageResult = task.resultDeferred.await().getOrAbort("Plugin content check for ${task.spec.id} skipped because packaging failed")
            collectPluginContentFailures(
              content = packageResult.content,
              project = packageResult.jpsProject,
              projectHome = packageResult.projectHome,
              suggestedReviewer = task.spec.suggestedReviewer,
              testName = { category, key -> "${task.spec.id} $category: $key" },
            )
          }
        }
      },
    )
  }
}

private fun createTargetValidationTasks(
  scope: CoroutineScope,
  spec: PackagingSuiteSpec,
  suiteContextDeferred: Deferred<PackagingSuiteContext>,
  packagingTasks: List<PackagingTask>,
  telemetry: PackagingSuiteTelemetry?,
): List<TargetValidationTask> {
  val packagingTasksByTargetId = packagingTasks.associateBy { it.spec.id }
  val result = ArrayList<TargetValidationTask>(spec.targetValidations.size)
  for (validation in spec.targetValidations) {
    val packagingTask = requireNotNull(packagingTasksByTargetId.get(validation.targetId)) {
      "Cannot find packaging target '${validation.targetId}' for target validation '${validation.name}'"
    }
    result.add(
      TargetValidationTask(
        spec = validation,
        packagingTask = packagingTask,
        resultDeferred = scope.async(start = CoroutineStart.LAZY) {
          captureTaskResult {
            withTelemetrySpan(
              telemetry = telemetry,
              name = "target validation: ${validation.targetId} ${validation.name}",
              configure = { span ->
                span.setAttribute("packaging.target.id", validation.targetId)
                span.setAttribute("packaging.validation.name", validation.name)
                span.setAttribute("packaging.validation.stage", validation.stage.name)
              },
            ) {
              val abortMessage = "Target validation '${validation.name}' for ${validation.targetId} skipped because packaging failed"
              val suiteContext = suiteContextDeferred.await()
              val layout = packagingTask.layoutDeferred.awaitOrAbort(abortMessage)
              val packageResultProvider: suspend () -> PackageResult = {
                packagingTask.resultDeferred.await().getOrAbort(abortMessage)
              }
              if (validation.stage == PackagingTargetValidationStage.CONTENT) {
                packageResultProvider()
              }
              spanBuilder("run target validation: ${validation.targetId} ${validation.name}").use {
                val validationTempDir = suiteContext.tempDir
                  .resolve("target-validation")
                  .resolve(validation.targetId)
                  .resolve(validation.name)
                  .createDirectories()
                validation.validator(
                  PackagingTargetValidationContext(
                    target = packagingTask.spec,
                    projectHome = layout.buildContext.paths.projectHome,
                    tempDir = validationTempDir,
                    project = layout.buildContext.project,
                    outputProvider = suiteContext.compilationContext.outputProvider,
                    layout = layout,
                    packageResultProvider = packageResultProvider,
                  )
                )
              }
            }
          }
        },
      )
    )
  }
  return result
}

private fun createDynamicTests(
  failures: List<PackagingCheckFailure>,
  problemMessage: String,
  threshold: Int,
  successTestName: String?,
): List<DynamicTest> {
  if (failures.isEmpty()) {
    return successTestName?.let { listOf(DynamicTest.dynamicTest(it) {}) } ?: emptyList()
  }
  if (failures.size <= threshold) {
    return failures.map { failure ->
      DynamicTest.dynamicTest(failure.name) {
        throw failure.error
      }
    }
  }

  return listOf(DynamicTest.dynamicTest("too many $problemMessage") {
    throw MultipleFailuresError("${failures.size} failures", failures.map { it.error })
  })
}

@Internal
fun createPluginContentDynamicTests(
  targetId: String,
  checkPlugins: Boolean,
  failures: List<PackagingCheckFailure> = emptyList(),
  failure: Throwable? = null,
): List<DynamicTest> {
  if (!checkPlugins) {
    return listOf(DynamicTest.dynamicTest(targetId) {})
  }
  if (failure != null) {
    return listOf(DynamicTest.dynamicTest(targetId) {
      throw failure
    })
  }
  return createDynamicTests(
    failures = failures,
    problemMessage = "Plugin content checks failed for $targetId",
    threshold = Int.MAX_VALUE,
    successTestName = targetId,
  )
}

private fun <T> TaskResult<T>.getOrThrow(): T {
  val failure = failure
  if (failure != null) {
    throw failure
  }
  return requireNotNull(value)
}

/**
 * The layout, or an abort with [message] when the packaging failed before it computed one.
 *
 * It mirrors [getOrAbort], which does the same for the packaged result.
 */
private suspend fun CompletableDeferred<PackagedLayout>.awaitOrAbort(message: String): PackagedLayout {
  try {
    return await()
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: TestAbortedException) {
    throw e
  }
  catch (e: Throwable) {
    throw TestAbortedException(message, e)
  }
}

private fun <T> TaskResult<T>.getOrAbort(message: String): T {
  val failure = failure
  if (failure != null) {
    if (failure is TestAbortedException) {
      throw failure
    }
    throw TestAbortedException(message, failure)
  }
  return requireNotNull(value)
}

private suspend fun <T> captureTaskResult(block: suspend () -> T): TaskResult<T> {
  return try {
    TaskResult(value = block())
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: Throwable) {
    TaskResult(failure = e)
  }
}

private suspend fun ensureBlockingValidationsSucceededOrAbort(blockingTasks: List<ValidationTask>) {
  for (task in blockingTasks) {
    val result = task.resultDeferred.await()
    val failure = result.failure
    if (failure != null) {
      throw TestAbortedException("Packaging skipped because suite validation '${task.spec.name}' failed", failure)
    }
    if (result.value.orEmpty().isNotEmpty()) {
      throw TestAbortedException("Packaging skipped because suite validation '${task.spec.name}' reported validation issues")
    }
  }
}

private fun ensureUniqueNames(kind: String, names: List<String>) {
  val seen = HashSet<String>(names.size)
  for (name in names) {
    check(seen.add(name)) { "Duplicate packaging $kind: $name" }
  }
}

private fun ensureTargetValidationsReferenceExistingTargets(spec: PackagingSuiteSpec) {
  val targetIds = spec.targets.mapTo(HashSet()) { it.id }
  for (validation in spec.targetValidations) {
    require(validation.targetId in targetIds) {
      "Cannot find packaging target '${validation.targetId}' for target validation '${validation.name}'"
    }
  }
}

private suspend fun createSharedCompilationContext(projectHome: Path, tempDir: Path, scope: CoroutineScope): CompilationContext {
  return createCompilationContext(
    projectHome = projectHome,
    buildOutputRootEvaluator = { tempDir },
    options = createBuildOptionsForTest(homeDir = projectHome, outDir = tempDir),
    setupTracer = false,
  ).toBazelIfNeeded(scope).asArchivedIfNeeded
}

private fun createPackagingBuildOptions(projectHome: Path, buildOutputRoot: Path) =
  createBuildOptionsForTest(homeDir = projectHome, outDir = buildOutputRoot).also {
    customizeBuildOptionsForPackagingContentTest(it)
  }

private fun createDerivedBuildContext(
  sharedCompilationContext: CompilationContext,
  target: PackagingTargetSpec,
  projectHome: Path,
  buildOutputRoot: Path,
): BuildContext {
  val productProperties = target.createProductProperties(projectHome).also { it.buildDocAuthoringAssets = false }
  val options = createPackagingBuildOptions(projectHome = projectHome, buildOutputRoot = buildOutputRoot)
  val logDir = buildOutputRoot.resolve("log").createDirectories()
  val tempDir = buildOutputRoot.resolve("temp").createDirectories()
  val paths = BuildPaths(
    communityHomeDirRoot = sharedCompilationContext.paths.communityHomeDirRoot,
    buildOutputDir = buildOutputRoot,
    logDir = logDir,
    projectHome = projectHome,
    artifactDir = buildOutputRoot.resolve("artifacts"),
    tempDir = tempDir,
  )
  val compilationContextCopy = sharedCompilationContext.createCopy(messages = BuildMessagesImpl.create(), options = options, paths = paths)
  return createBuildContext(
    compilationContext = compilationContextCopy,
    projectHome = projectHome,
    productProperties = productProperties,
    proprietaryBuildTools = target.buildTools,
  )
}

private suspend fun computePackageResult(context: BuildContext, layoutDeferred: CompletableDeferred<PackagedLayout>): PackageResult {
  return doRunTestBuild(
    context = context,
    writeTelemetry = false,
    checkIntegrityOfEmbeddedFrontend = false,
    checkThatBundledPluginInFrontendArePresent = false,
    traceSpanName = context.productProperties.baseFileName,
    build = { buildContext ->
      // the state is a suspending lazy of the build context, so `buildDistributions` reuses this one.
      val distributionState = spanBuilder("compute distribution state").use { buildContext.distributionState() }
      layoutDeferred.complete(PackagedLayout(buildContext = buildContext, distributionState = distributionState))
      buildDistributions(buildContext)
      PackageResult(
        content = spanBuilder("read content report").use {
          readContentReportZip(buildContext.paths.artifactDir.resolve("content-report.zip"))
        },
        runtimeModuleRepository = spanBuilder("read runtime module repository").use {
          readGeneratedRuntimeModuleRepository(buildContext)
        },
        jpsProject = buildContext.project,
        projectHome = buildContext.paths.projectHome,
      )
    },
  )
}

private fun readGeneratedRuntimeModuleRepository(buildContext: BuildContext): RuntimeModuleRepository? {
  val repositoryPath = findGeneratedRuntimeModuleRepository(buildContext) ?: return null
  val repository = RuntimeModuleRepository.create(repositoryPath)
  //force RuntimeModuleRepository to parse the file, otherwise it'll fail because the artifacts are deleted by doRunTestBuild before the packaging tests start
  repository.findModuleHeader(RuntimeModuleId.contentModule("intellij.platform.frontend", RuntimeModuleId.DEFAULT_NAMESPACE))
  return repository
}

private fun findGeneratedRuntimeModuleRepository(context: BuildContext): Path? {
  val commonFile = context.paths.distAllDir.resolve(MODULE_DESCRIPTORS_COMPACT_PATH)
  if (commonFile.exists()) {
    return commonFile
  }
  //ideally, we should run separate checks for different OS, but for now let's check only for the current one
  val currentDistribution = SUPPORTED_DISTRIBUTIONS.find { it.os == OsFamily.currentOs && it.arch == JvmArchitecture.currentJvmArch } ?: return null
  val osSpecificFile =
    getOsAndArchSpecificDistDirectory(currentDistribution.os, currentDistribution.arch, currentDistribution.libcImpl, context).resolve(MODULE_DESCRIPTORS_COMPACT_PATH)
  if (osSpecificFile.exists()) {
    return osSpecificFile
  }
  return null
}

@Internal
fun resolvePackagingSuiteTraceSettings(spec: PackagingSuiteSpec, testLogDir: Path = TestLoggerFactory.getTestLogDir()): PackagingSuiteTraceSettings {
  val traceFileProperty = System.getProperty(PACKAGING_SUITE_TRACE_FILE_PROPERTY)?.takeIf { it.isNotBlank() }
  val isEnabled = traceFileProperty != null || System.getProperty(PACKAGING_SUITE_TELEMETRY_ENABLED_PROPERTY)?.toBoolean() == true
  if (!isEnabled) {
    return PackagingSuiteTraceSettings(enabled = false, traceFile = null)
  }

  val traceFile = traceFileProperty
                    ?.let { rawPath ->
                      val path = Path.of(rawPath)
                      if (path.isAbsolute) path else spec.homePath.resolve(path)
                    }
                  ?: testLogDir.resolve("${spec.name}-packaging-trace.json")
  return PackagingSuiteTraceSettings(enabled = true, traceFile = traceFile)
}

private fun createSuiteTelemetry(spec: PackagingSuiteSpec, traceSettings: PackagingSuiteTraceSettings): PackagingSuiteTelemetry? {
  if (!traceSettings.enabled) {
    return null
  }

  val traceFile = requireNotNull(traceSettings.traceFile)
  runBlocking {
    JaegerJsonSpanExporterManager.setOutput(file = traceFile, addShutDownHook = false)
  }
  val rootSpan = spanBuilder("packaging suite: ${spec.name}").startSpan().also { span ->
    span.setAttribute("packaging.suite.name", spec.name)
    span.setAttribute("packaging.target.count", spec.targets.size.toLong())
    span.setAttribute("packaging.validation.count", spec.validations.size.toLong())
    span.setAttribute("packaging.trace.file", traceFile.toString())
  }
  return PackagingSuiteTelemetry(
    traceFile = traceFile,
    rootSpan = rootSpan,
    parentContext = Context.current().with(rootSpan),
  )
}

private suspend fun <T> withTelemetrySpan(
  telemetry: PackagingSuiteTelemetry?,
  name: String,
  configure: (Span) -> Unit = {},
  block: suspend () -> T,
): T {
  if (telemetry == null) {
    return block()
  }

  return spanBuilder(name).setParent(telemetry.parentContext).use { span ->
    configure(span)
    block()
  }
}
