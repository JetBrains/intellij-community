// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("HardCodedStringLiteral", "UnstableApiUsage", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceGetOrSet")

package org.jetbrains.bazel.jvm.jps

import com.intellij.openapi.diagnostic.IdeaLogRecordFormatter
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtilRt
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap
import kotlinx.coroutines.*
import org.apache.arrow.memory.RootAllocator
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.bazel.jvm.WorkRequest
import org.jetbrains.bazel.jvm.WorkRequestExecutor
import org.jetbrains.bazel.jvm.jps.impl.*
import org.jetbrains.bazel.jvm.jps.state.LoadStateResult
import org.jetbrains.bazel.jvm.jps.state.TargetConfigurationDigestContainer
import org.jetbrains.bazel.jvm.jps.state.TargetConfigurationDigestProperty
import org.jetbrains.bazel.jvm.jps.state.createInitialSourceMap
import org.jetbrains.bazel.jvm.jps.state.loadBuildState
import org.jetbrains.bazel.jvm.jps.state.saveBuildState
import org.jetbrains.bazel.jvm.kotlin.ArgMap
import org.jetbrains.bazel.jvm.kotlin.JvmBuilderFlags
import org.jetbrains.bazel.jvm.kotlin.parseArgs
import org.jetbrains.bazel.jvm.processRequests
import org.jetbrains.bazel.jvm.use
import org.jetbrains.jps.api.CanceledStatus
import org.jetbrains.jps.api.GlobalOptions
import org.jetbrains.jps.backwardRefs.JavaBackwardReferenceIndexBuilder
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType
import org.jetbrains.jps.incremental.CompileContextImpl
import org.jetbrains.jps.incremental.CompileScopeImpl
import org.jetbrains.jps.incremental.ModuleBuildTarget
import org.jetbrains.jps.incremental.RebuildRequestedException
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService
import org.jetbrains.jps.model.JpsModel
import org.jetbrains.kotlin.config.IncrementalCompilation
import org.jetbrains.kotlin.jps.incremental.KotlinCompilerReferenceIndexBuilder
import java.io.Writer
import java.nio.file.Files
import java.nio.file.Path
import java.util.Set
import kotlin.coroutines.coroutineContext

// if more than 50% files were changed, perform a full rebuild
private const val thresholdPercentage = 0.5

// Please note: for performance reasons, we do not set `jps.new.storage.compact.on.close` to true.
// As a result, the database file on disk may grow to some extent.

// kotlin.serialization.plugin.path
private fun configureKotlincHome() {
  val relativePath = requireNotNull(System.getProperty("jps.kotlin.home"))
  // todo a more robust solution to avoid `toRealPath`
  // resolve symlink to real dir
  val singleFile = Path.of(runFiles.rlocation(relativePath)).toRealPath()
  System.setProperty("jps.kotlin.home", singleFile.parent.toString())
}

fun configureGlobalJps(tracer: Tracer, scope: CoroutineScope) {
  val globalSpanForIJLogger = tracer.spanBuilder("global").startSpan()
  scope.coroutineContext.job.invokeOnCompletion { globalSpanForIJLogger.end() }

  Logger.setFactory { BazelLogger(category = IdeaLogRecordFormatter.smartAbbreviate(it), span = globalSpanForIJLogger) }
  System.setProperty("jps.service.manager.impl", BazelJpsServiceManager::class.java.name)
  System.setProperty("jps.backward.ref.index.builder.fs.case.sensitive", "true")
  System.setProperty(GlobalOptions.COMPILE_PARALLEL_MAX_THREADS_OPTION, Runtime.getRuntime().availableProcessors().toString())
  System.setProperty(GlobalOptions.COMPILE_PARALLEL_OPTION, "true")
  System.setProperty(GlobalOptions.DEPENDENCY_GRAPH_ENABLED, "true")
  System.setProperty(GlobalOptions.ALLOW_PARALLEL_AUTOMAKE_OPTION, "true")
  System.setProperty("idea.compression.enabled", "false")
  System.setProperty(IncrementalCompilation.INCREMENTAL_COMPILATION_JVM_PROPERTY, "true")
  configureKotlincHome()
}

internal class JpsBuildWorker private constructor(private val allocator: RootAllocator) : WorkRequestExecutor {
  companion object {
    @JvmStatic
    fun main(startupArgs: Array<String>) {
      RootAllocator(Long.MAX_VALUE).use { allocator ->
        processRequests(
          startupArgs = startupArgs,
          executor = JpsBuildWorker(allocator),
          setup = { tracer, scope -> configureGlobalJps(tracer = tracer, scope = scope) },
          serviceName = "jps-builder"
        )
      }
    }
  }

  @OptIn(ExperimentalStdlibApi::class)
  override suspend fun execute(request: WorkRequest, writer: Writer, baseDir: Path, tracingContext: Context, tracer: Tracer): Int {
    val dependencyFileToDigest = hashMap<Path, ByteArray>()
    val sourceFileToDigest = hashMap<Path, ByteArray>(request.inputPaths.size)
    val sources = ArrayList<Path>()
    val isDebugEnabled = request.verbosity > 0
    val sourceFileToDigestDebugString = if (isDebugEnabled) StringBuilder() else null
    val dependencyFileToDigestDebugString = if (isDebugEnabled) StringBuilder() else null
    for ((index, input) in request.inputPaths.withIndex()) {
      val digest = request.inputDigests[index]
      if (input.endsWith(".kt") || input.endsWith(".java")) {
        val file = baseDir.resolve(input).normalize()
        sources.add(file)
        sourceFileToDigest.put(file, digest)

        if (sourceFileToDigestDebugString != null) {
          appendDebug(sourceFileToDigestDebugString, input, digest)
        }
      }
      else if (input.endsWith(".jar")) {
        if (dependencyFileToDigestDebugString != null) {
          appendDebug(dependencyFileToDigestDebugString, input, digest)
        }
        dependencyFileToDigest.put(baseDir.resolve(input).normalize(), digest)
      }
    }

    if (isDebugEnabled) {
      tracer.spanBuilder("build")
        .setParent(tracingContext)
        .setAttribute(AttributeKey.stringKey("sourceFileToDigest"), sourceFileToDigestDebugString.toString())
        .setAttribute(AttributeKey.stringKey("dependencyFileToDigest"), dependencyFileToDigestDebugString.toString())
    }
    else {
      tracer.spanBuilder("build")
    }.use { span ->
      return buildUsingJps(
        baseDir = baseDir,
        args = parseArgs(request.arguments),
        out = writer,
        sources = sources,
        dependencyFileToDigest = dependencyFileToDigest,
        isDebugEnabled = isDebugEnabled,
        sourceFileToDigest = sourceFileToDigest,
        allocator = allocator,
        parentSpan = span,
        tracer = tracer,
        tracingContext = tracingContext.with(span),
      )
    }
  }
}

@OptIn(ExperimentalStdlibApi::class)
private fun appendDebug(debugString: java.lang.StringBuilder, input: String, digest: ByteArray) {
  debugString.append(input)
    .append(' ')
    .append(digest.toHexString())
    .append('\n')
}

@VisibleForTesting
suspend fun buildUsingJps(
  baseDir: Path,
  args: ArgMap<JvmBuilderFlags>,
  out: Writer,
  sources: List<Path>,
  dependencyFileToDigest: Map<Path, ByteArray>,
  sourceFileToDigest: Map<Path, ByteArray>,
  isDebugEnabled: Boolean,
  allocator: RootAllocator,
  parentSpan: Span,
  tracer: Tracer,
  tracingContext: Context,
  cachePrefix: String = "",
): Int {
  val log = RequestLog(out = out, parentSpan = parentSpan, tracer = tracer)

  val abiJar = args.optionalSingle(JvmBuilderFlags.ABI_OUT)?.let { baseDir.resolve(it).normalize() }
  val outJar = baseDir.resolve(args.mandatorySingle(JvmBuilderFlags.OUT)).normalize()
  val prefix = outJar.fileName.toString().removeSuffix(".jar")
  val bazelOutDir = outJar.parent
  val dataDir = bazelOutDir.resolve("$cachePrefix$prefix-jps-data")

  // incremental compilation - we do not clear dir
  val classOutDir = bazelOutDir.resolve("$cachePrefix$prefix-classes")

  val (jpsModel, targetDigests) = loadJpsModel(
    sources = sources,
    args = args,
    classPathRootDir = baseDir,
    classOutDir = classOutDir,
    dependencyFileToDigest = dependencyFileToDigest,
  )
  val moduleTarget = BazelModuleBuildTarget(
    outDir = classOutDir,
    module = jpsModel.project.modules.single(),
    sources = sources,
  )

  if (isDebugEnabled) {
    parentSpan.setAttribute("outJar", outJar.toString())
    parentSpan.setAttribute("abiJar", abiJar?.toString() ?: "")
    for (kind in TargetConfigurationDigestProperty.entries) {
      parentSpan.setAttribute(kind.name, targetDigests.get(kind))
    }
  }

  val relativizer = createPathRelativizer(baseDir = baseDir, classOutDir = classOutDir)

  // if class output dir doesn't exist, make sure that we do not to use existing cache -
  // set `isRebuild` to true and clear caches in this case
  var isRebuild = false
  if (Files.notExists(dataDir)) {
    FileUtilRt.deleteRecursively(classOutDir)
    isRebuild = true
  }
  else if (Files.notExists(classOutDir)) {
    FileUtilRt.deleteRecursively(dataDir)
    isRebuild = true
  }

  val buildStateFile = dataDir.resolve("$prefix-state-v1.arrow")
  val typeAwareRelativizer = relativizer.typeAwareRelativizer!!
  val buildState = tracer.spanBuilder("load and check state").setParent(tracingContext).use { parentSpan ->
    val buildState = if (isRebuild) {
      null
    }
    else {
      loadBuildState(
        buildStateFile = buildStateFile,
        relativizer = typeAwareRelativizer,
        allocator = allocator,
        sourceFileToDigest = sourceFileToDigest,
        targetDigests = targetDigests,
        parentSpan = parentSpan,
      )
    }

    val forceFullRebuild = buildState != null && checkIsFullRebuildRequired(
      buildState = buildState,
      log = log,
      sourceFileCount = sourceFileToDigest.size,
      parentSpan = parentSpan,
    )

    if (forceFullRebuild) {
      FileUtilRt.deleteRecursively(dataDir)
      FileUtilRt.deleteRecursively(classOutDir)

      isRebuild = true
    }

    buildState
  }

  var exitCode = initAndBuild(
    isRebuild = isRebuild,
    messageHandler = log,
    dataDir = dataDir,
    classOutDir = classOutDir,
    targetDigests = targetDigests,
    moduleTarget = moduleTarget,
    outJar = outJar,
    abiJar = abiJar,
    relativizer = relativizer,
    jpsModel = jpsModel,
    buildDataProvider = BazelBuildDataProvider(
      relativizer = typeAwareRelativizer,
      sourceToDescriptor = buildState?.map ?: createInitialSourceMap(sourceFileToDigest),
      storeFile = buildStateFile,
      allocator = allocator,
      isCleanBuild = isRebuild,
    ),
    buildState = buildState.takeIf { !isRebuild },
    tracingContext = tracingContext,
    parentSpan = parentSpan,
  )
  if (exitCode == -1) {
    log.resetState()
    exitCode = initAndBuild(
      isRebuild = true,
      messageHandler = log,
      dataDir = dataDir,
      classOutDir = classOutDir,
      targetDigests = targetDigests,
      moduleTarget = moduleTarget,
      outJar = outJar,
      abiJar = abiJar,
      relativizer = relativizer,
      jpsModel = jpsModel,
      buildDataProvider = BazelBuildDataProvider(
        relativizer = typeAwareRelativizer,
        sourceToDescriptor = hashMap(sourceFileToDigest.size),
        storeFile = buildStateFile,
        allocator = allocator,
        isCleanBuild = true,
      ),
      buildState = null,
      tracingContext = tracingContext,
      parentSpan = parentSpan,
    )
  }

  return exitCode
}

private fun checkIsFullRebuildRequired(
  buildState: LoadStateResult,
  log: RequestLog,
  sourceFileCount: Int,
  parentSpan: Span
): Boolean {
  if (buildState.rebuildRequested != null) {
    parentSpan.setAttribute("rebuildRequested", buildState.rebuildRequested)
    return true
  }

  val incrementalEffort = buildState.changedFiles.size + buildState.deletedFiles.size
  val rebuildThreshold = sourceFileCount * thresholdPercentage
  val forceFullRebuild = incrementalEffort >= rebuildThreshold
  log.out.appendLine("incrementalEffort=$incrementalEffort, rebuildThreshold=$rebuildThreshold, isFullRebuild=$forceFullRebuild")

  if (parentSpan.isRecording) {
    // do not use toRelative - print as is to show the actual path
    parentSpan.setAttribute(AttributeKey.stringArrayKey("changedFiles"), buildState.changedFiles.map { it.toString() })
    parentSpan.setAttribute(AttributeKey.stringArrayKey("deletedFiles"), buildState.deletedFiles.map { it.toString() })

    parentSpan.setAttribute("incrementalEffort", incrementalEffort.toLong())
    parentSpan.setAttribute("rebuildThreshold", rebuildThreshold)
    parentSpan.setAttribute("forceFullRebuild", forceFullRebuild)
  }
  return forceFullRebuild
}

private suspend fun initAndBuild(
  isRebuild: Boolean,
  messageHandler: RequestLog,
  dataDir: Path,
  classOutDir: Path,
  targetDigests: TargetConfigurationDigestContainer,
  moduleTarget: BazelModuleBuildTarget,
  outJar: Path,
  abiJar: Path?,
  relativizer: PathRelativizerService,
  jpsModel: JpsModel,
  buildDataProvider: BazelBuildDataProvider,
  buildState: LoadStateResult?,
  tracingContext: Context,
  parentSpan: Span,
): Int {
  val tracer = messageHandler.tracer
  val storageInitializer = StorageInitializer(dataDir = dataDir, classOutDir = classOutDir)
  val storageManager = tracer.spanBuilder("init storage").setParent(tracingContext).use { span ->
    if (isRebuild) {
        storageInitializer.clearAndInit(span)
      }
      else {
        storageInitializer.init(span)
      }
  }
  try {
    val projectDescriptor = storageInitializer.createProjectDescriptor(
      messageHandler = messageHandler,
      jpsModel = jpsModel,
      moduleTarget = moduleTarget,
      relativizer = relativizer,
      buildDataProvider = buildDataProvider,
      span = parentSpan,
    )
    try {
      val compileScope = CompileScopeImpl(
        /* types = */ Set.of(JavaModuleBuildTargetType.PRODUCTION),
        /* typesToForceBuild = */ Set.of(),
        /* targets = */ if (isRebuild) Set.of(moduleTarget) else Set.of(),
        /* files = */ java.util.Map.of()
      )

      val coroutineContext = coroutineContext
      val context = CompileContextImpl(
        compileScope,
        projectDescriptor,
        messageHandler,
        emptyMap(),
        object : CanceledStatus {
          override fun isCanceled(): Boolean = !coroutineContext.isActive
        },
      )

      val exitCode = tracer.spanBuilder("compile")
        .setParent(tracingContext)
        .setAttribute(AttributeKey.booleanKey("isRebuild"), isRebuild)
        .use { span ->
          val builders = arrayOf(
            JavaBuilder(span, messageHandler.out),
            //NotNullInstrumentingBuilder(),
            JavaBackwardReferenceIndexBuilder(),
            BazelKotlinBuilder(isKotlinBuilderInDumbMode = false, span = span, dataManager = buildDataProvider),
            KotlinCompilerReferenceIndexBuilder(),
          )
          builders.sortBy { it.category.ordinal }

          JpsTargetBuilder(
            log = messageHandler,
            isCleanBuild = storageInitializer.isCleanBuild,
            dataManager = buildDataProvider,
            tracer = tracer,
          ).build(
            context = context,
            moduleTarget = moduleTarget,
            builders = builders,
            buildState = buildState,
            tracingContext = tracingContext.with(span),
            parentSpan = span,
          )
        }
      try {
        postBuild(
          success = exitCode == 0,
          moduleTarget = moduleTarget,
          outJar = outJar,
          abiJar = abiJar,
          classOutDir = classOutDir,
          context = context,
          targetDigests = targetDigests,
          buildDataProvider = buildDataProvider,
          tracingContext = tracingContext,
          tracer = tracer,
        )
      }
      catch (e: Throwable) {
        // in case of any error during packaging - clear build
        //storageManager.forceClose()
        //projectDescriptor.release()

        //storageInitializer.clearStorage()

        throw e
      }
      return exitCode
    }
    catch (e: RebuildRequestedException) {
      parentSpan.recordException(e)
      return -1
    }
    finally {
      projectDescriptor.release()
    }
  }
  finally {
    storageManager.forceClose()
  }
}

private val stateFileMetaNames: Array<String> = TargetConfigurationDigestProperty.entries
  .let { entries -> Array(entries.size) { entries.get(it).name } }

private suspend fun postBuild(
  moduleTarget: ModuleBuildTarget,
  outJar: Path,
  abiJar: Path?,
  classOutDir: Path,
  context: CompileContextImpl,
  targetDigests: TargetConfigurationDigestContainer,
  buildDataProvider: BazelBuildDataProvider,
  tracingContext: Context,
  tracer: Tracer,
  success: Boolean,
) {
  coroutineScope {
    val dataManager = context.projectDescriptor.dataManager
    val sourceDescriptors = buildDataProvider.getFinalList()
    launch(CoroutineName("save caches")) {
      dataManager.flush(/* memoryCachesOnly = */ false)

      ensureActive()

      if (success) {
        // if success, then must be no changed files in the list
        val changedFiles = sourceDescriptors.filter { it.isChanged }
        require(changedFiles.isEmpty()) {
          "Compiled successfully, but still there are changed files: $changedFiles"
        }
      }

      saveBuildState(
        buildStateFile = buildDataProvider.storeFile,
        list = sourceDescriptors,
        relativizer = buildDataProvider.relativizer,
        metadata = Object2ObjectArrayMap(stateFileMetaNames, targetDigests.asString()),
        allocator = buildDataProvider.allocator,
      )
    }

    launch {
      // deletes class loader classpath index files for changed output roots
      // todo remove when we will produce JAR directly
      Files.deleteIfExists(classOutDir.resolve("classpath.index"))
      Files.deleteIfExists(classOutDir.resolve(".unmodified"))
    }

    if (success) {
      launch(CoroutineName("create output JAR and ABI JAR")) {
        // pack to jar
        tracer.spanBuilder("create output JAR and ABI JAR").setParent(tracingContext).use { span ->
          packageToJar(
            outJar = outJar,
            abiJar = abiJar,
            sourceDescriptors = sourceDescriptors,
            classOutDir = classOutDir,
            span = span,
          )
        }
      }
    }

    launch(CoroutineName("report build state")) {
      dataManager.reportUnhandledRelativizerPaths()
      reportRebuiltModules(context)
      reportUnprocessedChanges(context, moduleTarget)
    }
  }
}