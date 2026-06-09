// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "DestructuringDeclaration")

package com.intellij.platform.buildScripts.testFramework.distributionContent

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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildPaths
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.ProprietaryBuildTools
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
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

private data class PackageResult(
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
  @JvmField val requiresCompilation: Boolean = true,
  @JvmField val skipIfAborted: Boolean = true,
  @JvmField val validator: PackagingSuiteValidator,
)

@Internal
data class PackagingTargetValidationContext(
  @JvmField val target: PackagingTargetSpec,
  @JvmField val projectHome: Path,
  @JvmField val tempDir: Path,
  @JvmField val project: JpsProject,
  @JvmField val outputProvider: ModuleOutputProvider,
  @JvmField val runtimeModuleRepository: RuntimeModuleRepository?,
  @JvmField val content: ParsedContentReport,
)

@Internal
data class PackagingTargetValidationSpec(
  @JvmField val targetId: String,
  @JvmField val name: String,
  @JvmField val problemMessage: String,
  @JvmField val threshold: Int = Int.MAX_VALUE,
  @JvmField val alwaysCreateSuccessTest: Boolean = true,
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
  private val tempDir: Path,
  private val telemetry: PackagingSuiteTelemetry?,
  private val tracerOverride: AutoCloseable?,
  private val suiteContextDeferred: Deferred<PackagingSuiteContext>,
  private val validationTasks: List<ValidationTask>,
  private val packagingTasks: List<PackagingTask>,
  private val pluginCheckTasks: List<PluginCheckTask>,
  private val targetValidationTasks: List<TargetValidationTask>,
) : AutoCloseable {
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
      val traceSettings = resolvePackagingSuiteTraceSettings(spec)
      val telemetry = createSuiteTelemetry(spec = spec, traceSettings = traceSettings)
      val tracerOverride = traceSettings.takeUnless { it.enabled }?.let { TraceManager.pushTracer(packagingSuiteNoopTracer) }

      val scopeJob = SupervisorJob()

      @Suppress("RAW_SCOPE_CREATION")
      val scope = CoroutineScope(scopeJob + Dispatchers.Default)
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
        val compileProductionModulesDeferred = scope.async(start = CoroutineStart.LAZY) {
          withTelemetrySpan(
            telemetry = telemetry,
            name = "compile shared production modules",
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
          compileProductionModulesDeferred = compileProductionModulesDeferred,
          telemetry = telemetry,
        )
        val packagingTasks = createPackagingTasks(
          scope = scope,
          spec = spec,
          suiteContextDeferred = suiteContextDeferred,
          compileProductionModulesDeferred = compileProductionModulesDeferred,
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
      val taskResult = runBlocking { task.resultDeferred.await() }
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
        runBlocking {
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
        runBlocking {
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
    val resolvedCheckResults = runBlocking {
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
      val taskResult = runBlocking { task.resultDeferred.await() }
      val testName = "${task.spec.targetId} ${task.spec.name}"
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

  override fun close() {
    runBlocking { scopeJob.cancelAndJoin() }
    try {
      if (suiteContextDeferred.isCompleted) {
        runCatching { runBlocking { suiteContextDeferred.await().compilationContext.messages.close() } }
      }
      telemetry?.let {
        it.rootSpan.end()
        runCatching { runBlocking { TraceManager.flush() } }
        println("Packaging suite trace is written to ${it.traceFile}")
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

private fun scheduleFullSuiteWork(
  scope: CoroutineScope,
  validationTasks: List<ValidationTask>,
  packagingTasks: List<PackagingTask>,
  pluginCheckTasks: List<PluginCheckTask>,
  targetValidationTasks: List<TargetValidationTask>,
) {
  startBlockingValidationTasks(validationTasks)
  // Start non-blocking + source-only (no compile gate) validations immediately — they are independent
  // of compilation and of blocking validations, so they should overlap with model-generation/compile.
  validationTasks.startAllDeferreds { task ->
    if (!task.spec.isBlocking && !task.spec.requiresCompilation) task.resultDeferred else null
  }
  val targetValidationPackagingTasks = targetValidationTasks.mapTo(LinkedHashSet()) { it.packagingTask }
  targetValidationPackagingTasks.startAllPackagingTasks()
  targetValidationTasks.startAllDeferreds { it.resultDeferred }
  pluginCheckTasks.startAllDeferreds { task ->
    if (task.packagingTask in targetValidationPackagingTasks && task.packagingTask.spec.checkPlugins) task.resultDeferred else null
  }

  scope.launch(Dispatchers.Default) {
    validationTasks.filter { it.spec.isBlocking }.map { it.resultDeferred }.awaitAll()
    validationTasks.startAllDeferreds { task ->
      // independent ones already started above
      if (task.spec.isBlocking || !task.spec.requiresCompilation) null else task.resultDeferred
    }
    targetValidationPackagingTasks.map { it.resultDeferred }.awaitAll()

    val remainingPackagingTasks = packagingTasks.filter { it !in targetValidationPackagingTasks }
    remainingPackagingTasks.startAllPackagingTasks()
    val remainingPackagingTaskSet = remainingPackagingTasks.toHashSet()
    pluginCheckTasks.startAllDeferreds { task ->
      if (task.packagingTask in remainingPackagingTaskSet && task.packagingTask.spec.checkPlugins) task.resultDeferred else null
    }
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
  compileProductionModulesDeferred: Deferred<Unit>,
  telemetry: PackagingSuiteTelemetry?,
): List<ValidationTask> {
  val blockingTasks = ArrayList<ValidationTask>()
  for (validation in spec.validations) {
    if (!validation.isBlocking) {
      continue
    }

    blockingTasks.add(
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
              awaitPackagingSuiteValidationCompilationIfRequired(validation = validation, compileProductionModulesDeferred = compileProductionModulesDeferred)
              validation.validator(suiteContextDeferred.await())
            }
          }
        },
      )
    )
  }

  val blockingIterator = blockingTasks.iterator()
  val result = ArrayList<ValidationTask>(spec.validations.size)
  for (validation in spec.validations) {
    if (validation.isBlocking) {
      result.add(blockingIterator.next())
      continue
    }

    // A "source-only" non-blocking validation (no compilation gate) is independent of blocking validations
    // and can run in parallel with model-generation/compilation. It is scheduled immediately in
    // scheduleFullSuiteWork and skips the ensureBlockingValidationsSucceededOrAbort gate here.
    val runIndependently = !validation.requiresCompilation
    result.add(
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
              if (!runIndependently) {
                ensureBlockingValidationsSucceededOrAbort(blockingTasks)
                awaitPackagingSuiteValidationCompilationIfRequired(validation = validation, compileProductionModulesDeferred = compileProductionModulesDeferred)
              }
              validation.validator(suiteContextDeferred.await())
            }
          }
        },
      )
    )
  }
  return result
}

@Internal
suspend fun awaitPackagingSuiteValidationCompilationIfRequired(
  validation: PackagingSuiteValidationSpec,
  compileProductionModulesDeferred: Deferred<Unit>,
) {
  if (validation.requiresCompilation) {
    compileProductionModulesDeferred.await()
  }
}

private fun createPackagingTasks(
  scope: CoroutineScope,
  spec: PackagingSuiteSpec,
  suiteContextDeferred: Deferred<PackagingSuiteContext>,
  compileProductionModulesDeferred: Deferred<Unit>,
  validationTasks: List<ValidationTask>,
  telemetry: PackagingSuiteTelemetry?,
  waitForScheduledStart: Boolean,
): List<PackagingTask> {
  val blockingTasks = validationTasks.filter { it.spec.isBlocking }
  val result = ArrayList<PackagingTask>(spec.targets.size)
  for (target in spec.targets) {
    val startSignal = if (waitForScheduledStart) CompletableDeferred<Unit>() else null
    val coroutineStart = if (waitForScheduledStart) CoroutineStart.DEFAULT else CoroutineStart.LAZY
    result.add(
      PackagingTask(
        spec = target,
        startSignal = startSignal,
        resultDeferred = scope.async(start = coroutineStart) {
          startSignal?.await()
          captureTaskResult {
            withTelemetrySpan(
              telemetry = telemetry,
              name = "package target: ${target.id}",
              configure = { span ->
                span.setAttribute("packaging.target.id", target.id)
              },
            ) {
              ensureBlockingValidationsSucceededOrAbort(blockingTasks)
              compileProductionModulesDeferred.await()
              val suiteContext = suiteContextDeferred.await()
              val context = createDerivedBuildContext(
                sharedCompilationContext = suiteContext.compilationContext,
                target = target,
                projectHome = spec.homePath,
                buildOutputRoot = suiteContext.tempDir.resolve(target.id),
              )
              computePackageResult(context = context)
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
              },
            ) {
              val suiteContext = suiteContextDeferred.await()
              val packageResult = packagingTask.resultDeferred.await()
                .getOrAbort("Target validation '${validation.name}' for ${validation.targetId} skipped because packaging failed")
              spanBuilder("run target validation: ${validation.targetId} ${validation.name}").use {
                val validationTempDir = suiteContext.tempDir
                  .resolve("target-validation")
                  .resolve(validation.targetId)
                  .resolve(validation.name)
                  .createDirectories()
                validation.validator(
                  PackagingTargetValidationContext(
                    target = packagingTask.spec,
                    projectHome = packageResult.projectHome,
                    tempDir = validationTempDir,
                    project = packageResult.jpsProject,
                    outputProvider = suiteContext.compilationContext.outputProvider,
                    runtimeModuleRepository = packageResult.runtimeModuleRepository,
                    content = packageResult.content,
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

private suspend fun computePackageResult(context: BuildContext): PackageResult {
  return doRunTestBuild(
    context = context,
    writeTelemetry = false,
    checkIntegrityOfEmbeddedFrontend = false,
    checkThatBundledPluginInFrontendArePresent = false,
    traceSpanName = context.productProperties.baseFileName,
    build = { buildContext ->
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
