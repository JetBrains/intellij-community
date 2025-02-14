// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("HardCodedStringLiteral", "UnstableApiUsage", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceGetOrSet")

package org.jetbrains.bazel.jvm.jps

import com.intellij.openapi.diagnostic.IdeaLogRecordFormatter
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtilRt
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.apache.arrow.memory.RootAllocator
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.bazel.jvm.*
import org.jetbrains.bazel.jvm.abi.JarContentToProcess
import org.jetbrains.bazel.jvm.abi.writeAbi
import org.jetbrains.bazel.jvm.jps.impl.*
import org.jetbrains.bazel.jvm.jps.java.BazelJavaBuilder
import org.jetbrains.bazel.jvm.jps.kotlin.IncrementalKotlinBuilder
import org.jetbrains.bazel.jvm.jps.kotlin.NonIncrementalKotlinBuilder
import org.jetbrains.bazel.jvm.jps.state.*
import org.jetbrains.bazel.jvm.kotlin.JvmBuilderFlags
import org.jetbrains.bazel.jvm.kotlin.parseArgs
import org.jetbrains.jps.api.GlobalOptions
import org.jetbrains.jps.backwardRefs.JavaBackwardReferenceIndexBuilder
import org.jetbrains.jps.builders.logging.BuildLoggingManager
import org.jetbrains.jps.cmdline.ProjectDescriptor
import org.jetbrains.jps.incremental.ModuleBuildTarget
import org.jetbrains.jps.incremental.RebuildRequestedException
import org.jetbrains.jps.incremental.fs.BuildFSState
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService
import org.jetbrains.jps.model.JpsModel
import org.jetbrains.kotlin.config.IncrementalCompilation
import org.jetbrains.kotlin.jps.incremental.KotlinCompilerReferenceIndexBuilder
import java.io.Writer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.coroutines.coroutineContext

// if more than 50% files were changed, perform a full rebuild
private const val thresholdPercentage = 0.5

// Please note: for performance reasons, we do not set `jps.new.storage.compact.on.close` to true.
// As a result, the database file on disk may grow to some extent.

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
}

internal class JpsBuildWorker private constructor(private val allocator: RootAllocator) : WorkRequestExecutor<WorkRequestWithDigests> {
  companion object {
    @JvmStatic
    fun main(startupArgs: Array<String>) {
      RootAllocator(Long.MAX_VALUE).use { allocator ->
        processRequests(
          startupArgs = startupArgs,
          executor = JpsBuildWorker(allocator),
          setup = { tracer, scope -> configureGlobalJps(tracer = tracer, scope = scope) },
          reader = WorkRequestWithDigestReader(System.`in`),
          serviceName = "jps-builder",
        )
      }
    }
  }

  override suspend fun execute(request: WorkRequestWithDigests, writer: Writer, baseDir: Path, tracer: Tracer): Int {
    return incrementalBuild(request = request, baseDir = baseDir, tracer = tracer, writer = writer, allocator = allocator)
  }
}

private suspend fun incrementalBuild(
  request: WorkRequestWithDigests,
  baseDir: Path,
  tracer: Tracer,
  writer: Writer,
  allocator: RootAllocator,
): Int {
  val dependencyFileToDigest = if (isLibTracked) null else hashMap<Path, ByteArray>()
  val sourceFileToDigest = hashMap<Path, ByteArray>(request.inputPaths.size)
  val sources = ArrayList<Path>()
  val isDebugEnabled = request.verbosity > 0
  val sourceFileToDigestDebugString = if (isDebugEnabled) StringBuilder() else null
  val dependencyFileToDigestDebugString = if (isDebugEnabled) StringBuilder() else null
  for ((index, input) in request.inputPaths.withIndex()) {
    val digest = request.inputDigests.get(index)
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
      dependencyFileToDigest?.put(baseDir.resolve(input).normalize(), digest)
    }
  }

  return if (isDebugEnabled) {
    tracer.spanBuilder("build")
      .setAttribute(AttributeKey.stringKey("sourceFileToDigest"), sourceFileToDigestDebugString.toString())
      .setAttribute(AttributeKey.stringKey("dependencyFileToDigest"), dependencyFileToDigestDebugString.toString())
  }
  else {
    tracer.spanBuilder("build")
  }
    .use { span ->
      buildUsingJps(
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
      )
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
  dependencyFileToDigest: Map<Path, ByteArray>?,
  sourceFileToDigest: Map<Path, ByteArray>,
  isDebugEnabled: Boolean,
  allocator: RootAllocator,
  parentSpan: Span,
  tracer: Tracer,
  cachePrefix: String = "",
  forceIncremental: Boolean = false,
): Int {
  val log = RequestLog(out = out, parentSpan = parentSpan, tracer = tracer)

  val abiJar = args.optionalSingle(JvmBuilderFlags.ABI_OUT)?.let { baseDir.resolve(it).normalize() }
  val outJar = baseDir.resolve(args.mandatorySingle(JvmBuilderFlags.OUT)).normalize()
  val prefix = outJar.fileName.toString().removeSuffix(".jar")
  val bazelOutDir = outJar.parent
  val dataDir = bazelOutDir.resolve("$cachePrefix$prefix-jps-data")

  val (jpsModel, targetDigests) = loadJpsModel(
    sources = sources,
    args = args,
    classPathRootDir = baseDir,
    dependencyFileToDigest = dependencyFileToDigest,
  )

  val moduleTarget = BazelModuleBuildTarget(
    module = jpsModel.project.modules.single(),
    sources = sources,
  )

  val isIncrementalCompilation = forceIncremental || args.boolFlag(JvmBuilderFlags.INCREMENTAL)
  if (!isIncrementalCompilation) {
    return nonIncrementalBuildUsingJps(
      log = log,
      baseDir = baseDir,
      args = args,
      isDebugEnabled = isDebugEnabled,
      parentSpan = parentSpan,
      tracer = tracer,
      jpsModel = jpsModel,
      moduleTarget = moduleTarget,
    )
  }

  if (isDebugEnabled) {
    parentSpan.setAttribute("outJar", outJar.toString())
    parentSpan.setAttribute("abiJar", abiJar?.toString() ?: "")
    for (kind in TargetConfigurationDigestProperty.entries) {
      parentSpan.setAttribute(kind.name, targetDigests.get(kind))
    }
  }

  val relativizer = createPathRelativizer(baseDir = baseDir)

  // if class jar doesn't exist, make sure that we do not to use existing cache -
  // set `isRebuild` to true and clear caches in this case
  var isRebuild = false
  if (Files.notExists(outJar)) {
    FileUtilRt.deleteRecursively(dataDir)
    isRebuild = true
  }

  val buildStateFile = dataDir.resolve("$prefix-state-v1.arrow")
  val typeAwareRelativizer = relativizer.typeAwareRelativizer!!

  fun computeBuildState(parentSpan: Span): LoadStateResult? {
    val buildState = loadBuildState(
      buildStateFile = buildStateFile,
      relativizer = typeAwareRelativizer,
      allocator = allocator,
      sourceFileToDigest = sourceFileToDigest,
      targetDigests = targetDigests,
      parentSpan = parentSpan,
    )

    val forceFullRebuild = buildState != null && checkIsFullRebuildRequired(
      buildState = buildState,
      log = log,
      sourceFileCount = sourceFileToDigest.size,
      parentSpan = parentSpan,
    )
    if (forceFullRebuild) {
      FileUtilRt.deleteRecursively(dataDir)

      isRebuild = true
      return null
    }
    else {
      return buildState
    }
  }

  val buildState = if (isRebuild) null else tracer.span("load and check state") { parentSpan -> computeBuildState(parentSpan) }

  var exitCode = initAndBuild(
    compileScope = BazelCompileScope(isIncrementalCompilation = true, isRebuild = isRebuild),
    messageHandler = log,
    dataDir = dataDir,
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
    buildState = buildState,
    parentSpan = parentSpan,
    isDebugEnabled = isDebugEnabled,
  )

  if (exitCode == -1) {
    log.resetState()
    exitCode = initAndBuild(
      compileScope = BazelCompileScope(isIncrementalCompilation = true, isRebuild = true),
      messageHandler = log,
      dataDir = dataDir,
      targetDigests = targetDigests,
      moduleTarget = moduleTarget,
      outJar = outJar,
      abiJar = abiJar,
      relativizer = relativizer,
      jpsModel = jpsModel,
      buildDataProvider = BazelBuildDataProvider(
        relativizer = typeAwareRelativizer,
        sourceToDescriptor = createInitialSourceMap(sourceFileToDigest),
        storeFile = buildStateFile,
        allocator = allocator,
        isCleanBuild = true,
      ),
      buildState = null,
      parentSpan = parentSpan,
      isDebugEnabled = isDebugEnabled,
    )
  }

  return exitCode
}

private suspend fun nonIncrementalBuildUsingJps(
  baseDir: Path,
  args: ArgMap<JvmBuilderFlags>,
  isDebugEnabled: Boolean,
  parentSpan: Span,
  tracer: Tracer,
  log: RequestLog,
  jpsModel: JpsModel,
  moduleTarget: BazelModuleBuildTarget,
): Int {
  val abiJar = args.optionalSingle(JvmBuilderFlags.ABI_OUT)?.let { baseDir.resolve(it).normalize() }
  val outJar = baseDir.resolve(args.mandatorySingle(JvmBuilderFlags.OUT)).normalize()
  if (isDebugEnabled) {
    parentSpan.setAttribute("outJar", outJar.toString())
    parentSpan.setAttribute("abiJar", abiJar?.toString() ?: "")
  }

  val projectDescriptor = ProjectDescriptor(
    /* model = */ jpsModel,
    /* fsState = */ BuildFSState(/* alwaysScanFS = */ true),
    /* dataManager = */
    null,
    /* loggingManager = */ BuildLoggingManager.DEFAULT,
    /* moduleExcludeIndex = */ NoopModuleExcludeIndex,
    /* buildTargetIndex = */ BazelBuildTargetIndex(moduleTarget),
    /* buildRootIndex = */ BazelBuildRootIndex(moduleTarget),
    /* ignoredFileIndex = */ NoopIgnoredFileIndex,
  )

  val context = BazelCompileContext(
    scope = BazelCompileScope(isIncrementalCompilation = false, isRebuild = true),
    projectDescriptor = projectDescriptor,
    delegateMessageHandler = log,
    coroutineContext = coroutineContext,
  )

  // non-incremental build - oldJar is always as null (no need to copy old unchanged files)
  OutputSink.createOutputSink(oldJar = null).use { outputSink ->
    val exitCode = tracer.spanBuilder("compile").use { span ->
      val builders = arrayOf(
        BazelJavaBuilder(isIncremental = false, tracer = tracer, isDebugEnabled = isDebugEnabled, out = log.out),
        //NotNullInstrumentingBuilder(),
        NonIncrementalKotlinBuilder(job = coroutineContext.job, span = span),
      )
      builders.sortBy { it.category.ordinal }

      JpsTargetBuilder(
        log = log,
        isCleanBuild = true,
        dataManager = null,
        tracer = tracer,
      ).build(
        context = context,
        moduleTarget = moduleTarget,
        builders = builders,
        buildState = null,
        outputSink = outputSink,
        parentSpan = span,
      )
    }
    if (exitCode == 0) {
      writeJarAndAbi(tracer = tracer, outputSink = outputSink, outJar = outJar, abiJar = abiJar, sourceDescriptors = null)
    }
    return exitCode
  }
}

private suspend fun writeJarAndAbi(
  tracer: Tracer,
  outputSink: OutputSink,
  outJar: Path,
  abiJar: Path?,
  sourceDescriptors: Array<SourceDescriptor>?,
) {
  coroutineScope {
    if (abiJar == null) {
      tracer.span("write output JAR") {
        outputSink.writeToZip(outJar = outJar, classChannel = null, outputToSource = emptyMap())
      }
    }
    else {
      val outputToSource = if (sourceDescriptors != null) {
        val outputToSource = hashMap<String, String>(sourceDescriptors.size)
        for (sourceDescriptor in sourceDescriptors) {
          for (output in sourceDescriptor.outputs) {
            outputToSource.put(output, sourceDescriptor.sourceFile.toString())
          }
        }
        outputToSource
      }
      else {
        emptyMap()
      }

      tracer.span("create output JAR and ABI JAR") {
        val classChannel = Channel<JarContentToProcess>(capacity = 16)
        launch {
          outputSink.writeToZip(outJar = outJar, classChannel = classChannel, outputToSource = outputToSource)
          classChannel.close()
        }
        withContext(Dispatchers.IO) {
          writeAbi(abiJar, classChannel)
        }
      }
    }
  }
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
  compileScope: BazelCompileScope,
  messageHandler: RequestLog,
  dataDir: Path,
  targetDigests: TargetConfigurationDigestContainer,
  moduleTarget: BazelModuleBuildTarget,
  outJar: Path,
  abiJar: Path?,
  relativizer: PathRelativizerService,
  jpsModel: JpsModel,
  buildDataProvider: BazelBuildDataProvider,
  buildState: LoadStateResult?,
  parentSpan: Span,
  isDebugEnabled: Boolean,
): Int {
  val isRebuild = compileScope.isRebuild
  val tracer = messageHandler.tracer
  val storageInitializer = StorageInitializer(dataDir = dataDir, outJar = outJar)
  val storageManager = tracer.span("init storage") { span ->
    if (isRebuild) {
      storageInitializer.clearAndInit(span)
    }
    else {
      storageInitializer.init(span)
    }
  }
  try {
    val projectDescriptor = storageInitializer.createProjectDescriptor(
      jpsModel = jpsModel,
      moduleTarget = moduleTarget,
      relativizer = relativizer,
      buildDataProvider = buildDataProvider,
      span = parentSpan,
    )
    try {
      val context = BazelCompileContext(
        scope = compileScope,
        projectDescriptor = projectDescriptor,
        delegateMessageHandler = messageHandler,
        coroutineContext = coroutineContext,
      )

      // We remove `outJar` if a rebuild is detected as necessary.
      // Therefore, the `Files.exists` condition is enough.
      // However, it is still better to avoid unnecessary I/O calls.
      OutputSink.createOutputSink(oldJar = if (isRebuild) null else outJar.takeIf { Files.exists(it) }).use { outputSink ->
        val exitCode = tracer.spanBuilder("compile")
          .setAttribute(AttributeKey.booleanKey("isRebuild"), isRebuild)
          .use { span ->
            val builders = arrayOf(
              if (compileScope.isIncrementalCompilation) {
                IncrementalKotlinBuilder(isRebuild = isRebuild, span = span, dataManager = buildDataProvider, jpsTarget = moduleTarget)
              }
              else {
                NonIncrementalKotlinBuilder(job = coroutineContext.job, span = span)
              },
              BazelJavaBuilder(
                isIncremental = compileScope.isIncrementalCompilation,
                tracer = tracer,
                isDebugEnabled = isDebugEnabled,
                out = messageHandler.out,
              ),
              //NotNullInstrumentingBuilder(),
              JavaBackwardReferenceIndexBuilder(),
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
              outputSink = outputSink,
              parentSpan = span,
            )
          }
        try {
          coroutineScope {
            postBuild(
              success = exitCode == 0,
              moduleTarget = moduleTarget,
              outJar = outJar,
              abiJar = abiJar,
              context = context,
              targetDigests = targetDigests,
              buildDataProvider = buildDataProvider,
              tracer = tracer,
              outputSink = outputSink,
              // We remove `outJar` if a rebuild is detected as necessary.
              // Therefore, the `Files.exists` condition is enough.
              // However, it is still better to avoid unnecessary I/O calls.
              parentSpan = parentSpan,
            )
          }
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

private fun CoroutineScope.postBuild(
  moduleTarget: ModuleBuildTarget,
  outJar: Path,
  abiJar: Path?,
  context: BazelCompileContext,
  targetDigests: TargetConfigurationDigestContainer,
  buildDataProvider: BazelBuildDataProvider,
  tracer: Tracer,
  success: Boolean,
  outputSink: OutputSink,
  parentSpan: Span,
) {
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
    writeJarAndAbi(tracer = tracer, outputSink = outputSink, outJar = outJar, abiJar = abiJar, sourceDescriptors = sourceDescriptors)
  }

  launch(CoroutineName("report build state")) {
    dataManager.reportUnhandledRelativizerPaths()
    if (context.projectDescriptor.fsState.hasUnprocessedChanges(context, moduleTarget)) {
      parentSpan.addEvent("Some files were changed during the build. Additional compilation may be required.")
    }
  }
}