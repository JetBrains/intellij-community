// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("HardCodedStringLiteral", "UnstableApiUsage", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceGetOrSet")
package org.jetbrains.bazel.jvm.worker

import androidx.collection.MutableScatterMap
import androidx.collection.ScatterMap
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.arrow.memory.RootAllocator
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.bazel.jvm.kotlin.IncrementalKotlinBuilder
import org.jetbrains.bazel.jvm.kotlin.JvmBuilderFlags
import org.jetbrains.bazel.jvm.kotlin.parseArgs
import org.jetbrains.bazel.jvm.span
import org.jetbrains.bazel.jvm.use
import org.jetbrains.bazel.jvm.util.ArgMap
import org.jetbrains.bazel.jvm.worker.core.BazelBuildDataProvider
import org.jetbrains.bazel.jvm.worker.core.BazelCompileContext
import org.jetbrains.bazel.jvm.worker.core.BazelCompileScope
import org.jetbrains.bazel.jvm.worker.core.BazelModuleBuildTarget
import org.jetbrains.bazel.jvm.worker.core.BazelPathTypeAwareRelativizer
import org.jetbrains.bazel.jvm.worker.core.RequestLog
import org.jetbrains.bazel.jvm.worker.core.createPathRelativizer
import org.jetbrains.bazel.jvm.worker.core.output.OutputSink
import org.jetbrains.bazel.jvm.worker.core.output.createOutputSink
import org.jetbrains.bazel.jvm.worker.core.output.writeJarAndAbi
import org.jetbrains.bazel.jvm.worker.dependencies.DependencyAnalyzer
import org.jetbrains.bazel.jvm.worker.impl.JpsTargetBuilder
import org.jetbrains.bazel.jvm.worker.impl.createJpsProjectDescriptor
import org.jetbrains.bazel.jvm.worker.java.BazelJavaBuilder
import org.jetbrains.bazel.jvm.worker.state.SourceFileStateResult
import org.jetbrains.bazel.jvm.worker.state.TargetConfigurationDigestContainer
import org.jetbrains.bazel.jvm.worker.state.TargetConfigurationDigestProperty
import org.jetbrains.bazel.jvm.worker.state.createInitialSourceMap
import org.jetbrains.bazel.jvm.worker.state.saveBuildState
import org.jetbrains.bazel.jvm.worker.storage.StorageInitializer
import org.jetbrains.bazel.jvm.worker.storage.ToolOrStorageFormatChanged
import org.jetbrains.jps.backwardRefs.JavaBackwardReferenceIndexBuilder
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService
import org.jetbrains.jps.incremental.storage.BuildDataManager
import org.jetbrains.jps.model.JpsModel
import org.jetbrains.kotlin.jps.incremental.KotlinCompilerReferenceIndexBuilder
import java.io.Writer
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CancellationException
import kotlin.coroutines.coroutineContext

internal suspend fun incrementalBuild(
  request: WorkRequestWithDigests,
  baseDir: Path,
  tracer: Tracer,
  writer: Writer,
  allocator: RootAllocator,
  dependencyAnalyzer: DependencyAnalyzer,
): Int {
  val dependencyFileToDigest = MutableScatterMap<Path, ByteArray>()
  val sourceFileToDigest = MutableScatterMap<Path, ByteArray>(request.inputPaths.size)
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
      dependencyFileToDigest.put(baseDir.resolve(input).normalize(), digest)
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
        dependencyAnalyzer = dependencyAnalyzer,
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

internal const val INCREMENTAL_CACHE_DIRECTORY_SUFFIX = "-ic"

@VisibleForTesting
suspend fun buildUsingJps(
  baseDir: Path,
  args: ArgMap<JvmBuilderFlags>,
  out: Writer,
  sources: List<Path>,
  dependencyFileToDigest: ScatterMap<Path, ByteArray>,
  sourceFileToDigest: ScatterMap<Path, ByteArray>,
  isDebugEnabled: Boolean,
  allocator: RootAllocator,
  parentSpan: Span,
  tracer: Tracer,
  dependencyAnalyzer: DependencyAnalyzer,
  cachePrefix: String = "",
  forceIncremental: Boolean = false,
): Int {
  val relativizer = createPathRelativizer(baseDir)
  val typeAwareRelativizer = relativizer.typeAwareRelativizer as BazelPathTypeAwareRelativizer
  val log = RequestLog(out = out, parentSpan = parentSpan, tracer = tracer, relativizer = typeAwareRelativizer)

  val outJar = baseDir.resolve(args.mandatorySingle(JvmBuilderFlags.OUT)).normalize()
  val prefix = outJar.fileName.toString().removeSuffix(".jar")
  val bazelOutDir = outJar.parent
  val dataDir = bazelOutDir.resolve("$cachePrefix$prefix$INCREMENTAL_CACHE_DIRECTORY_SUFFIX")
  val outputs = OutputFiles(
    outJar = outJar,
    abiJar = args.optionalSingle(JvmBuilderFlags.ABI_OUT)?.let { baseDir.resolve(it).normalize() },
    dataDir = dataDir,
  )

  val (jpsModel, targetDigests) = loadJpsModel(
    sources = sources,
    args = args,
    classPathRootDir = baseDir,
    dependencyFileToDigest = dependencyFileToDigest,
  )

  val moduleTarget = BazelModuleBuildTarget(
    module = jpsModel.project.modules.single(),
    sources = sources,
    javaFileCount = args.optionalSingle(JvmBuilderFlags.JAVA_COUNT)?.toInt() ?: -1,
    targetLabel = args.mandatorySingle(JvmBuilderFlags.TARGET_LABEL),
  )

  val isIncrementalCompilation = !args.boolFlag(JvmBuilderFlags.NON_INCREMENTAL) || forceIncremental
  if (isDebugEnabled) {
    parentSpan.setAttribute("isIncrementalCompilation", isIncrementalCompilation)
    parentSpan.setAttribute("outJar", outJar.toString())
    parentSpan.setAttribute("abiJar", outputs.abiJar?.toString() ?: "")
    for (kind in TargetConfigurationDigestProperty.entries) {
      parentSpan.setAttribute(kind.name, targetDigests.get(kind))
    }
  }

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

  var rebuildReason = validateFileExistence(outputs = outputs, cacheDir = dataDir)
  val buildStateFile = dataDir.resolve("$prefix-state-v1.arrow")

  val sourceFileState = if (rebuildReason == null) {
    val buildStateResult = tracer.span("load and check state") {
      computeBuildState(
        buildStateFile = buildStateFile,
        sourceRelativizer = typeAwareRelativizer.sourceRelativizer,
        allocator = allocator,
        sourceFileToDigest = sourceFileToDigest,
        forceIncremental = forceIncremental,
        tracer = tracer,
        parentSpan = it,
      )
    }
    rebuildReason = buildStateResult.rebuildRequested
    buildStateResult.sourceFileState
  }
  else {
    null
  }

  val isRebuild = rebuildReason != null
  if (isRebuild && isDebugEnabled) {
    log.out.appendLine("rebuild reason: $rebuildReason")
  }

  var exitCode = try {
    initAndBuild(
      rebuildReason = rebuildReason,
      compileScope = BazelCompileScope(isIncrementalCompilation = true, isRebuild = isRebuild),
      requestLog = log,
      dataDir = dataDir,
      targetDigests = targetDigests,
      moduleTarget = moduleTarget,
      outputs = outputs,
      relativizer = relativizer,
      jpsModel = jpsModel,
      dataManager = BazelBuildDataProvider(
        relativizer = typeAwareRelativizer,
        sourceToDescriptor = sourceFileState?.map ?: createInitialSourceMap(sourceFileToDigest),
        storeFile = buildStateFile,
        allocator = allocator,
        dependencyFileToDigest = dependencyFileToDigest,
      ),
      sourceFileState = sourceFileState,
      isDebugEnabled = isDebugEnabled,
      dependencyAnalyzer = dependencyAnalyzer,
    )
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: ToolOrStorageFormatChanged) {
    rebuildReason = e.message
    if (isDebugEnabled) {
      log.out.appendLine("rebuild requested: $rebuildReason")
    }
    parentSpan.recordException(e)
    -1
  }
  catch (e: Throwable) {
    if (isDebugEnabled) {
      log.out.appendLine("rebuild requested: ${e.stackTraceToString()}")
    }
    parentSpan.recordException(e)
    rebuildReason = e.cause?.message ?: e.message ?: "unknown error"
    -1
  }

  if (exitCode == -1) {
    log.resetState()
    exitCode = initAndBuild(
      rebuildReason = rebuildReason,
      compileScope = BazelCompileScope(isIncrementalCompilation = true, isRebuild = true),
      requestLog = log,
      dataDir = dataDir,
      targetDigests = targetDigests,
      moduleTarget = moduleTarget,
      outputs = outputs,
      relativizer = relativizer,
      jpsModel = jpsModel,
      dataManager = BazelBuildDataProvider(
        relativizer = typeAwareRelativizer,
        sourceToDescriptor = createInitialSourceMap(sourceFileToDigest),
        storeFile = buildStateFile,
        allocator = allocator,
        dependencyFileToDigest = dependencyFileToDigest,
      ),
      sourceFileState = null,
      isDebugEnabled = isDebugEnabled,
      dependencyAnalyzer = dependencyAnalyzer,
    )
  }

  return exitCode
}

internal fun createJavaBuilder(
  tracer: Tracer,
  isIncremental: Boolean,
  isDebugEnabled: Boolean,
  javaFileCount: Int,
  out: Appendable,
): BazelJavaBuilder? {
  // we skip javaBuilder only for non-incremental, because for incremental
  if (javaFileCount == 0) {
    return null
  }
  else {
    return BazelJavaBuilder(isIncremental = isIncremental, tracer = tracer, isDebugEnabled = isDebugEnabled, out = out)
  }
}

internal class OutputFiles(
  @JvmField val outJar: Path,
  @JvmField val abiJar: Path?,
  dataDir: Path,
) {
  @JvmField val cachedJar: Path = dataDir.resolve(outJar.fileName)
  @JvmField val cachedAbiJar: Path? = abiJar?.let { dataDir.resolve(it.fileName) }
}

@OptIn(DelicateCoroutinesApi::class)
private suspend fun initAndBuild(
  rebuildReason: String?,
  compileScope: BazelCompileScope,
  requestLog: RequestLog,
  dataDir: Path,
  targetDigests: TargetConfigurationDigestContainer,
  moduleTarget: BazelModuleBuildTarget,
  outputs: OutputFiles,
  relativizer: PathRelativizerService,
  jpsModel: JpsModel,
  dataManager: BazelBuildDataProvider,
  sourceFileState: SourceFileStateResult?,
  dependencyAnalyzer: DependencyAnalyzer,
  isDebugEnabled: Boolean,
): Int {
  val isRebuild = compileScope.isRebuild
  val tracer = requestLog.tracer
  val storageInitializer = StorageInitializer(dataDir, dataDir.resolve(dataDir.fileName.toString() + ".db"))
  var storageClosed = false
  val buildDataManager = tracer.spanBuilder("init storage")
    .setAttribute("isRebuild", isRebuild)
    .setAttribute("rebuildReason", rebuildReason ?: "")
    .use { span ->
      storageInitializer.createBuildDataManager(
        isRebuild = isRebuild,
        relativizer = relativizer,
        buildDataProvider = dataManager,
        targetDigests = targetDigests,
        span = span,
      )
    }
  try {
    val context = BazelCompileContext(
      scope = compileScope,
      projectDescriptor = createJpsProjectDescriptor(jpsModel = jpsModel, moduleTarget = moduleTarget, dataManager = buildDataManager),
      delegateMessageHandler = requestLog,
      coroutineContext = coroutineContext,
    )

    val oldJar: Path?
    val oldAbiJar: Path?
    if (isRebuild) {
      oldJar = null
      oldAbiJar = null
    }
    else {
      oldJar = outputs.cachedJar.takeIf { Files.exists(it) }
      oldAbiJar = if (oldJar == null || outputs.cachedAbiJar == null) null else outputs.cachedAbiJar.takeIf { Files.exists(it) }
    }

    createOutputSink(oldJar = oldJar, oldAbiJar = oldAbiJar, withAbi = outputs.abiJar != null).use { outputSink ->
      val exitCode = tracer.spanBuilder("compile")
        .setAttribute(AttributeKey.booleanKey("isRebuild"), isRebuild)
        .use { span ->
          val builders = arrayOf(
            IncrementalKotlinBuilder(
              isRebuild = isRebuild,
              span = span,
              dataManager = dataManager,
              jpsTarget = moduleTarget,
              tracer = tracer,
            ),
            // If not rebuilding, we still need to create a JavaBuilder even if there are no Java files,
            // as there might be some old ones cached (so, we have to update an incremental cache).
            if (isRebuild && moduleTarget.javaFileCount == 0) {
              null
            }
            else {
              createJavaBuilder(
                tracer = tracer,
                isDebugEnabled = isDebugEnabled,
                out = requestLog.out,
                isIncremental = true,
                javaFileCount = moduleTarget.javaFileCount,
              )
            },
            JavaBackwardReferenceIndexBuilder(),
            KotlinCompilerReferenceIndexBuilder(),
          ).filterNotNull().toTypedArray()
          builders.sortBy { it.category.ordinal }

          JpsTargetBuilder(
            log = requestLog,
            dataManager = dataManager,
            tracer = tracer,
          ).build(
            context = context,
            moduleTarget = moduleTarget,
            builders = builders,
            buildState = sourceFileState,
            outputSink = outputSink,
            dependencyAnalyzer = dependencyAnalyzer,
            parentSpan = span,
          )
        }

      try {
        tracer.span("postBuild") { span ->
          postBuild(
            buildDataManager = buildDataManager,
            success = exitCode == 0,
            moduleTarget = moduleTarget,
            outputs = outputs,
            context = context,
            buildDataProvider = dataManager,
            requestLog = requestLog,
            outputSink = outputSink,
            parentSpan = span,
          )
          storageClosed = true
        }
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Throwable) {
        // in case of error during packaging - clear build
        try {
          buildDataManager.forceClose()
          storageClosed = true
        }
        finally {
          storageInitializer.clearStorage()
        }

        throw e
      }
      return exitCode
    }
  }
  finally {
    if (!storageClosed) {
      buildDataManager.forceClose()
    }
  }
}

private fun CoroutineScope.postBuild(
  moduleTarget: BazelModuleBuildTarget,
  outputs: OutputFiles,
  context: BazelCompileContext,
  buildDataProvider: BazelBuildDataProvider,
  requestLog: RequestLog,
  success: Boolean,
  outputSink: OutputSink,
  parentSpan: Span,
  buildDataManager: BuildDataManager,
) {
  val sourceDescriptors = buildDataProvider.getFinalList()
  launch(CoroutineName("save caches")) {
    withContext(Dispatchers.IO) {
      buildDataManager.close()
    }

    if (success) {
      // if success, then must be no changed files in the list
      val changedFiles = sourceDescriptors.asSequence().filter { it.isChanged }
      require(changedFiles.none()) {
        "Compiled successfully, but still there are changed files: ${changedFiles.toList()}"
      }
    }
  }

  launch {
    saveBuildState(
      buildStateFile = buildDataProvider.storeFile,
      list = sourceDescriptors,
      relativizer = buildDataProvider.relativizer.sourceRelativizer,
      allocator = buildDataProvider.allocator,
    )
  }

  launch {
    if (outputSink.isChanged || context.scope.isRebuild) {
      writeJarAndAbi(
        tracer = requestLog.tracer,
        outputSink = outputSink,
        outJar = outputs.cachedJar,
        abiJar = outputs.cachedAbiJar,
      )
    }
    else {
      parentSpan.addEvent("no changes detected, no output JAR will be produced")
    }

    // copy to output
    withContext(Dispatchers.IO) {
      Files.deleteIfExists(outputs.outJar)
      Files.createLink(outputs.outJar, outputs.cachedJar)
      if (outputs.abiJar != null) {
        Files.deleteIfExists(outputs.abiJar)
        Files.createLink(outputs.abiJar, outputs.cachedAbiJar)
      }
    }
  }

  launch(CoroutineName("report build state")) {
    buildDataManager.relativizer.reportUnhandledPaths()
    if (context.projectDescriptor.fsState.hasUnprocessedChanges(context, moduleTarget)) {
      parentSpan.addEvent("Some files were changed during the build. Additional compilation may be required.")
    }
  }
}