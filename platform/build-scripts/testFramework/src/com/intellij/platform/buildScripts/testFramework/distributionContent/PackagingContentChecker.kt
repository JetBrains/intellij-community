// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "DestructuringDeclaration")

package com.intellij.platform.buildScripts.testFramework.distributionContent

import com.intellij.openapi.util.io.NioFiles
import com.intellij.platform.buildScripts.concurrency.Joiner
import com.intellij.platform.buildScripts.concurrency.TaskScope
import com.intellij.platform.buildScripts.concurrency.TaskSignal
import com.intellij.platform.buildScripts.testFramework.createBuildOptionsForTest
import com.intellij.platform.buildScripts.testFramework.customizeBuildOptionsForPackagingContentTest
import com.intellij.platform.buildScripts.testFramework.doRunTestBuild
import com.intellij.platform.runtime.repository.RuntimeModuleId
import com.intellij.platform.runtime.repository.RuntimeModuleRepository
import com.intellij.testFramework.TestLoggerFactory
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.TracerProvider
import io.opentelemetry.context.Context
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildLifetime
import java.util.concurrent.CancellationException
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
import org.jetbrains.intellij.build.impl.buildDistributions
import org.jetbrains.intellij.build.impl.createBuildContext
import org.jetbrains.intellij.build.impl.createCompilationContext
import org.jetbrains.intellij.build.impl.getOsAndArchSpecificDistDirectory
import org.jetbrains.intellij.build.impl.logging.BuildMessagesImpl
import org.jetbrains.intellij.build.impl.toArchivedIfNeeded
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
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

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
  @JvmField val resultDeferred: PackagingTaskHandle<TaskResult<List<PackagingCheckFailure>>>,
)

private data class TargetValidationTask(
  @JvmField val spec: PackagingTargetValidationSpec,
  @JvmField val packagingTask: PackagingTask,
  @JvmField val resultDeferred: PackagingTaskHandle<TaskResult<List<PackagingCheckFailure>>>,
)

private data class PackagingTask(
  @JvmField val spec: PackagingTargetSpec,
  @JvmField val startSignal: TaskSignal<Unit>?,
  /**
   * The layout of the target, which the build computes before it packs a jar.
   *
   * A packaging task that fails before it computes the layout completes this exceptionally, so a `LAYOUT` validation
   * aborts instead of waiting for a result that never comes.
   */
  @JvmField val layoutDeferred: TaskSignal<PackagedLayout>,
  @JvmField val resultDeferred: PackagingTaskHandle<TaskResult<PackageResult>>,
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
  @JvmField val resultDeferred: PackagingTaskHandle<TaskResult<List<PackagingCheckFailure>>>,
)

private inline fun <T> Iterable<T>.startAllDeferreds(getDeferred: (T) -> PackagingTaskHandle<*>?) {
  for (item in this) {
    getDeferred(item)?.start()
  }
}

private fun Iterable<PackagingTask>.startAllPackagingTasks() {
  for (task in this) {
    task.start()
  }
}

typealias PackagingSuiteValidator = (context: PackagingSuiteContext) -> List<PackagingCheckFailure>
typealias PackagingTargetValidator = (context: PackagingTargetValidationContext) -> List<PackagingCheckFailure>

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
  @JvmField val lifetime: BuildLifetime,
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
  @JvmField val productObservationFile: Path,
  @JvmField val project: JpsProject,
  @JvmField val outputProvider: ModuleOutputProvider,
  @JvmField val layout: PackagedLayout,
  private val packageResultProvider: () -> PackageResult,
) {
  /** The content report of the packaged distribution. A [PackagingTargetValidationStage.LAYOUT] validation must not read it. */
  fun content(): ParsedContentReport = packageResultProvider().content

  /** The runtime module repository of the packaged distribution, or `null` when the build generated none. */
  fun runtimeModuleRepository(): RuntimeModuleRepository? = packageResultProvider().runtimeModuleRepository
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
  /** The project-relative path of the compact report used as the test expectation. */
  @JvmField val contentYamlPath: String,
  @JvmField val createProductProperties: (projectHome: Path) -> ProductProperties,
  @JvmField val buildTools: ProprietaryBuildTools = ProprietaryBuildTools.DUMMY,
  @JvmField val checkPlugins: Boolean = true,
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
  private val supervisor: Thread,
  private val lifetime: BuildLifetime,
  private val diagnostics: PackagingSuiteHangDiagnostics,
  private val tempDir: Path,
  private val telemetry: PackagingSuiteTelemetry?,
  private val tracerOverride: AutoCloseable?,
  private val suiteContextDeferred: PackagingTaskHandle<PackagingSuiteContext>,
  private val validationTasks: List<ValidationTask>,
  private val packagingTasks: List<PackagingTask>,
  private val pluginCheckTasks: List<PluginCheckTask>,
  private val targetValidationTasks: List<TargetValidationTask>,
) : AutoCloseable {
  private var closed = false

  companion object {
    fun create(spec: PackagingSuiteSpec): PackagingSuiteFixture {
      require(spec.targets.isNotEmpty()) { "Packaging suite must contain at least one target" }
      ensureUniqueNames(kind = "target", names = spec.targets.map { it.id })
      ensureUniqueNames(kind = "validation", names = spec.validations.map { it.name })
      ensureUniqueNames(kind = "target validation", names = spec.targetValidations.map { "${it.targetId}:${it.name}" })
      ensureTargetValidationsReferenceExistingTargets(spec)

      val ready = TaskSignal<PackagingSuiteFixture>("create packaging fixture")
      val created = java.util.concurrent.atomic.AtomicReference<PackagingSuiteFixture>()
      val supervisor = Thread.ofVirtual().name("${spec.name} supervisor").unstarted {
        try {
          TaskScope.open(name = spec.name, joiner = Joiner.awaitAll()).use { tasks ->
            val fixture = createSharedFixture(spec, tasks, Thread.currentThread())
            created.set(fixture)
            ready.complete(fixture)
            tasks.join()
          }
        }
        catch (failure: Throwable) {
          ready.fail(failure)
        }
      }
      supervisor.start()
      try {
        return ready.await()
      }
      catch (failure: Throwable) {
        supervisor.interrupt()
        awaitThreadTermination(supervisor)
        try {
          created.get()?.close()
        }
        catch (cleanupFailure: Throwable) {
          if (cleanupFailure !== failure) failure.addSuppressed(cleanupFailure)
        }
        if (failure is InterruptedException) Thread.currentThread().interrupt()
        throw failure
      }
    }

    private fun createSharedFixture(spec: PackagingSuiteSpec, tasks: TaskScope, supervisor: Thread): PackagingSuiteFixture {
      val traceSettings = resolvePackagingSuiteTraceSettings(spec)
      val telemetry = createSuiteTelemetry(spec = spec, traceSettings = traceSettings)
      val tracerOverride = traceSettings.takeUnless { it.enabled }?.let { TraceManager.pushTracer(packagingSuiteNoopTracer) }

      val diagnostics = PackagingSuiteHangDiagnostics()
      val scope = PackagingTasks(tasks, diagnostics)
      // the module output caches of the shared compilation context live as long as the fixture
      val lifetime = BuildLifetime()
      var tempDirForCleanup: Path? = null
      try {
        val tempDir = Files.createTempDirectory("${spec.name}-packaging-suite-").also { tempDirForCleanup = it }
        val suiteContextDeferred = scope.task(name = "create compilation context") {
          withTelemetrySpan(telemetry = telemetry, name = "create shared compilation context") {
            run {
              PackagingSuiteContext(
                projectHome = spec.homePath,
                tempDir = tempDir,
                lifetime = lifetime,
                compilationContext = createSharedCompilationContext(projectHome = spec.homePath, tempDir = tempDir, lifetime = lifetime),
              )
            }
          }
        }
        // The gate compiles nothing. Under Bazel `compileModules` has an empty body, and a JPS run reuses the
        // project output, so the call resolves the project dependencies and marks the output as available. The
        // suite does it once, and every derived context shares the result through `JpsCompilationData`.
        val moduleOutputDeferred = scope.task(name = "prepare module output") {
          withTelemetrySpan(
            telemetry = telemetry,
            name = "prepare shared module output",
            configure = { span ->
              span.setAttribute("packaging.target.count", spec.targets.size.toLong())
            },
          ) {
            val context = suiteContextDeferred.await().compilationContext
            run { context.compileProductionModules() }
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
          supervisor = supervisor,
          lifetime = lifetime,
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
        runCatching { tasks.close() }
        runCatching { lifetime.close() }
        runCatching { tracerOverride?.close() }
        telemetry?.rootSpan?.end()
        runCatching { TraceManager.flush() }
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

  fun createPluginTests(): List<DynamicTest> {
    if (!isOptimizedFullSuiteScheduling()) {
      startBlockingValidationTasks()
      packagingTasks.filter { it.spec.checkPlugins }.startAllPackagingTasks()
    }
    pluginCheckTasks.startAllDeferreds { it.resultDeferred }

    val tests = ArrayList<DynamicTest>(packagingTasks.size)
    val resolvedCheckResults = awaitTask("plugin content checks") {
      pluginCheckTasks.map { it.resultDeferred.await() }
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

  private fun <T> awaitTask(what: String, block: () -> T): T {
    return awaitOnTestThread(what = what, diagnostics = diagnostics, block = block)
  }

  @Synchronized
  override fun close() {
    if (closed) return
    closed = true
    var interrupted = Thread.interrupted()
    var failure: Throwable? = null
    fun clean(action: () -> Unit) {
      try {
        action()
      }
      catch (error: Throwable) {
        val previous = failure
        if (previous == null) failure = error else if (previous !== error) previous.addSuppressed(error)
      }
      finally {
        interrupted = Thread.interrupted() || interrupted
      }
    }
    try {
      supervisor.interrupt()
      clean { awaitTask("termination of the fixture") { awaitThreadTermination(supervisor) } }
      clean { lifetime.close() }
      if (suiteContextDeferred.isDone) {
        val context = runCatching { suiteContextDeferred.await() }.getOrNull()
        if (context != null) clean { context.compilationContext.messages.close() }
      }
      telemetry?.let {
        clean { it.rootSpan.end() }
        clean { TraceManager.flush() }
        println("Packaging suite trace is written to ${it.traceFile}")
      }
      clean { tracerOverride?.close() }
      clean { NioFiles.deleteRecursively(tempDir) }
      failure?.let { throw it }
    }
    finally {
      if (interrupted) Thread.currentThread().interrupt()
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

private fun scheduleFullSuiteWork(
  scope: PackagingTasks,
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

  scope.task(name = "schedule packaging", startImmediately = true) {
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

@Internal
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class PackagingSuiteTestBase {
  protected abstract val packagingFixture: PackagingSuiteFixture

  @TestFactory
  fun suiteValidations(): List<DynamicTest> = packagingFixture.createSuiteValidationTests()

  @TestFactory
  fun build(): List<DynamicTest> = packagingFixture.createBuildTests()

  @TestFactory
  fun plugins(): List<DynamicTest> = packagingFixture.createPluginTests()

  @TestFactory
  fun targetValidations(): List<DynamicTest> = packagingFixture.createTargetValidationTests()
}

private fun createValidationTasks(
  scope: PackagingTasks,
  spec: PackagingSuiteSpec,
  suiteContextDeferred: PackagingTaskHandle<PackagingSuiteContext>,
  moduleOutputDeferred: PackagingTaskHandle<Unit>,
  telemetry: PackagingSuiteTelemetry?,
): List<ValidationTask> {
  return spec.validations.map { validation ->
    ValidationTask(
      spec = validation,
      resultDeferred = scope.task(startImmediately = false) {
        captureTaskResult {
          withTelemetrySpan(
            telemetry = telemetry,
            name = "suite validation: ${validation.name}",
            configure = { span ->
              span.setAttribute("packaging.validation.name", validation.name)
            },
          ) {
            moduleOutputDeferred.await()
            val context = suiteContextDeferred.await()
            run { validation.validator(context) }
          }
        }
      },
    )
  }
}

private fun createPackagingTasks(
  scope: PackagingTasks,
  spec: PackagingSuiteSpec,
  suiteContextDeferred: PackagingTaskHandle<PackagingSuiteContext>,
  moduleOutputDeferred: PackagingTaskHandle<Unit>,
  validationTasks: List<ValidationTask>,
  telemetry: PackagingSuiteTelemetry?,
  waitForScheduledStart: Boolean,
): List<PackagingTask> {
  val blockingTasks = validationTasks.filter { it.spec.isBlocking }
  val result = ArrayList<PackagingTask>(spec.targets.size)
  for (target in spec.targets) {
    val startSignal = if (waitForScheduledStart) TaskSignal<Unit>() else null
    val layoutDeferred = TaskSignal<PackagedLayout>()
    result.add(
      PackagingTask(
        spec = target,
        startSignal = startSignal,
        layoutDeferred = layoutDeferred,
        resultDeferred = scope.task(name = "package ${target.id}", startImmediately = waitForScheduledStart) {
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
                run {
                  val context = createDerivedBuildContext(
                    sharedCompilationContext = suiteContext.compilationContext,
                    lifetime = suiteContext.lifetime,
                    target = target,
                    projectHome = spec.homePath,
                    buildOutputRoot = suiteContext.tempDir.resolve(target.id),
                  )
                  computePackageResult(context = context, layoutDeferred = layoutDeferred)
                }
              }
            }
            // the task can fail before it computes the layout, and a LAYOUT validation waits for the layout alone.
            // `completeExceptionally` does nothing when the layout is there already.
            taskResult.failure?.let { layoutDeferred.fail(it) }
            taskResult
          }
          finally {
            // `captureTaskResult` rethrows a cancellation, so a cancelled task reaches no line above that completes the
            // layout. A LAYOUT validation must abort then, not wait for a layout that never comes.
            if (!layoutDeferred.isDone) {
              layoutDeferred.fail(IllegalStateException("Packaging of '${target.id}' was cancelled before it computed the layout"))
            }
          }
        },
      )
    )
  }
  return result
}

private fun createPluginCheckTasks(
  scope: PackagingTasks,
  packagingTasks: List<PackagingTask>,
  telemetry: PackagingSuiteTelemetry?,
): List<PluginCheckTask> {
  return packagingTasks.map { task ->
    PluginCheckTask(
      packagingTask = task,
      resultDeferred = scope.task(startImmediately = false) {
        if (!task.spec.checkPlugins) {
          return@task TaskResult(value = emptyList())
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
              testName = { category, key -> "${task.spec.id} $category: $key" },
            )
          }
        }
      },
    )
  }
}

private fun createTargetValidationTasks(
  scope: PackagingTasks,
  spec: PackagingSuiteSpec,
  suiteContextDeferred: PackagingTaskHandle<PackagingSuiteContext>,
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
        resultDeferred = scope.task(startImmediately = false) {
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
              val packageResultProvider: () -> PackageResult = {
                packagingTask.resultDeferred.await().getOrAbort(abortMessage)
              }
              if (validation.stage == PackagingTargetValidationStage.CONTENT) {
                packagingTask.resultDeferred.await().getOrAbort(abortMessage)
              }
              run {
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
                      productObservationFile = TestLoggerFactory.getTestLogDir().resolve("packaging-reports")
                        .resolve(spec.name).resolve("${validation.targetId}.yaml"),
                      project = layout.buildContext.project,
                      outputProvider = suiteContext.compilationContext.outputProvider,
                      layout = layout,
                      packageResultProvider = packageResultProvider,
                    )
                  )
                }
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
private fun TaskSignal<PackagedLayout>.awaitOrAbort(message: String): PackagedLayout {
  try {
    return await()
  }
  catch (e: InterruptedException) {
    throw e
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

private fun <T> captureTaskResult(block: () -> T): TaskResult<T> {
  return try {
    TaskResult(value = block())
  }
  catch (e: InterruptedException) {
    throw e
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: Throwable) {
    TaskResult(failure = e)
  }
}

private fun ensureBlockingValidationsSucceededOrAbort(blockingTasks: List<ValidationTask>) {
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

private fun createSharedCompilationContext(projectHome: Path, tempDir: Path, lifetime: BuildLifetime): CompilationContext {
  return createCompilationContext(
    projectHome = projectHome,
    buildOutputRootEvaluator = { tempDir },
    options = createBuildOptionsForTest(homeDir = projectHome, outDir = tempDir),
    setupTracer = false,
  ).toBazelIfNeeded(lifetime).toArchivedIfNeeded(lifetime)
}

private fun createPackagingBuildOptions(projectHome: Path, buildOutputRoot: Path) =
  createBuildOptionsForTest(homeDir = projectHome, outDir = buildOutputRoot).also {
    customizeBuildOptionsForPackagingContentTest(it)
  }

private fun createDerivedBuildContext(
  sharedCompilationContext: CompilationContext,
  lifetime: BuildLifetime,
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
    lifetime = lifetime,
  )
}

private fun computePackageResult(context: BuildContext, layoutDeferred: TaskSignal<PackagedLayout>): PackageResult {
  return doRunTestBuild(
    context = context,
    closeLifetime = false,
    writeTelemetry = false,
    checkIntegrityOfEmbeddedFrontend = false,
    checkThatBundledPluginInFrontendArePresent = false,
    traceSpanName = context.productProperties.baseFileName,
    build = { buildContext ->
      val distributionState = spanBuilder("compute distribution state").use { buildContext.distributionState() }
      layoutDeferred.complete(PackagedLayout(buildContext = buildContext, distributionState = distributionState))
      val projectedContentReport = requireNotNull(buildDistributions(buildContext)) {
        "The build of ${buildContext.productProperties.baseFileName} skipped the product distributions, so there is no content to check"
      }
      PackageResult(
        content = ParsedContentReport(
          platform = projectedContentReport.platform,
          productModules = projectedContentReport.productModules,
          bundled = projectedContentReport.bundled,
          nonBundled = projectedContentReport.nonBundled,
        ),
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
  JaegerJsonSpanExporterManager.setOutput(file = traceFile, addShutDownHook = false)
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

private fun <T> withTelemetrySpan(
  telemetry: PackagingSuiteTelemetry?,
  name: String,
  configure: (Span) -> Unit = {},
  block: () -> T,
): T {
  if (telemetry == null) {
    return block()
  }

  return spanBuilder(name).setParent(telemetry.parentContext).use { span ->
    configure(span)
    block()
  }
}
