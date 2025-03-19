// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("HardCodedStringLiteral", "UnstableApiUsage", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceGetOrSet")

package org.jetbrains.bazel.jvm.jps

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.arrow.memory.RootAllocator
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.bazel.jvm.ArgMap
import org.jetbrains.bazel.jvm.hashMap
import org.jetbrains.bazel.jvm.jps.impl.BazelBuildDataProvider
import org.jetbrains.bazel.jvm.jps.impl.BazelCompileContext
import org.jetbrains.bazel.jvm.jps.impl.BazelCompileScope
import org.jetbrains.bazel.jvm.jps.impl.BazelModuleBuildTarget
import org.jetbrains.bazel.jvm.jps.impl.BazelPathTypeAwareRelativizer
import org.jetbrains.bazel.jvm.jps.impl.JpsTargetBuilder
import org.jetbrains.bazel.jvm.jps.impl.RequestLog
import org.jetbrains.bazel.jvm.jps.impl.createJpsProjectDescriptor
import org.jetbrains.bazel.jvm.jps.impl.createPathRelativizer
import org.jetbrains.bazel.jvm.jps.java.BazelJavaBuilder
import org.jetbrains.bazel.jvm.jps.kotlin.IncrementalKotlinBuilder
import org.jetbrains.bazel.jvm.jps.output.OutputSink
import org.jetbrains.bazel.jvm.jps.output.createOutputSink
import org.jetbrains.bazel.jvm.jps.output.writeJarAndAbi
import org.jetbrains.bazel.jvm.jps.state.DependencyStateStorage
import org.jetbrains.bazel.jvm.jps.state.SourceFileStateResult
import org.jetbrains.bazel.jvm.jps.state.TargetConfigurationDigestContainer
import org.jetbrains.bazel.jvm.jps.state.TargetConfigurationDigestProperty
import org.jetbrains.bazel.jvm.jps.state.createInitialSourceMap
import org.jetbrains.bazel.jvm.jps.state.createNewDependencyList
import org.jetbrains.bazel.jvm.jps.state.saveBuildState
import org.jetbrains.bazel.jvm.jps.storage.StorageInitializer
import org.jetbrains.bazel.jvm.kotlin.JvmBuilderFlags
import org.jetbrains.bazel.jvm.kotlin.parseArgs
import org.jetbrains.bazel.jvm.span
import org.jetbrains.bazel.jvm.use
import org.jetbrains.jps.backwardRefs.JavaBackwardReferenceIndexBuilder
import org.jetbrains.jps.incremental.dependencies.DependencyAnalyzer
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
  val dependencyFileToDigest = hashMap<Path, ByteArray>()
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
  val dataDir = bazelOutDir.resolve("$cachePrefix$prefix-jps-data")
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
  val depStateStorageFile = dataDir.resolve("$prefix-lib-roots-v2.arrow")
  val trackableDependencyFiles = moduleTarget.module.container.getChild(BazelConfigurationHolder.KIND).trackableDependencyFiles

  val buildState = if (rebuildReason == null) {
    tracer.span("load and check state") {
      computeBuildState(
        buildStateFile = buildStateFile,
        depStateStorageFile = depStateStorageFile,
        trackableDependencyFiles = trackableDependencyFiles,
        sourceRelativizer = typeAwareRelativizer.sourceRelativizer,
        allocator = allocator,
        sourceFileToDigest = sourceFileToDigest,
        targetDigests = targetDigests,
        forceIncremental = forceIncremental,
        tracer = tracer,
        dependencyFileToDigest = dependencyFileToDigest,
        parentSpan = it,
      )
    }.also {
      rebuildReason = it.rebuildRequested
    }
  }
  else {
    createCleanBuildStateResult(trackableDependencyFiles, dependencyFileToDigest, rebuildReason)
  }

  val isRebuild = rebuildReason != null
  if (isRebuild) {
    log.out.appendLine("rebuild reason: $rebuildReason")
  }

  var exitCode = try {
    initAndBuild(
      rebuildReason = rebuildReason,
      compileScope = BazelCompileScope(isIncrementalCompilation = true, dependencyAnalyzer = dependencyAnalyzer, isRebuild = isRebuild),
      requestLog = log,
      dataDir = dataDir,
      targetDigests = targetDigests,
      moduleTarget = moduleTarget,
      outputs = outputs,
      relativizer = relativizer,
      jpsModel = jpsModel,
      dataManager = BazelBuildDataProvider(
        relativizer = typeAwareRelativizer,
        sourceToDescriptor = buildState.sourceFileState?.map ?: createInitialSourceMap(sourceFileToDigest),
        storeFile = buildStateFile,
        allocator = allocator,
        isCleanBuild = isRebuild,
        libRootManager = DependencyStateStorage(
          storageFile = depStateStorageFile,
          state = buildState.dependencyState,
        ),
      ),
      sourceFileState = buildState.sourceFileState,
      isDebugEnabled = isDebugEnabled,
    )
  }
  catch (e: CancellationException) {
    throw e
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
      compileScope = BazelCompileScope(isIncrementalCompilation = true, dependencyAnalyzer = dependencyAnalyzer, isRebuild = true),
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
        isCleanBuild = true,
        libRootManager = DependencyStateStorage(
          storageFile = depStateStorageFile,
          state = createNewDependencyList(trackableDependencyFiles, dependencyFileToDigest),
        ),
      ),
      sourceFileState = null,
      isDebugEnabled = isDebugEnabled,
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
            // as there might be some old ones cached (so, we have to update incremental cache).
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
            //NotNullInstrumentingBuilder(),
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
            targetDigests = targetDigests,
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

private val stateFileMetaNames: Array<String> = TargetConfigurationDigestProperty.entries
  .let { entries -> Array(entries.size) { entries.get(it).name } }

private fun CoroutineScope.postBuild(
  moduleTarget: BazelModuleBuildTarget,
  outputs: OutputFiles,
  context: BazelCompileContext,
  targetDigests: TargetConfigurationDigestContainer,
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
      metadata = Object2ObjectArrayMap(stateFileMetaNames, targetDigests.asString()),
      allocator = buildDataProvider.allocator,
    )
  }
  launch {
    buildDataProvider.libRootManager.saveState(buildDataProvider.allocator, buildDataProvider.relativizer.sourceRelativizer)
  }

  launch {
    if (outputSink.isChanged || context.scope.isRebuild) {
      writeJarAndAbi(
        tracer = requestLog.tracer,
        outputSink = outputSink,
        outJar = outputs.cachedJar,
        abiJar = outputs.cachedAbiJar,
        sourceDescriptors = sourceDescriptors,
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
    buildDataManager.reportUnhandledRelativizerPaths()
    if (context.projectDescriptor.fsState.hasUnprocessedChanges(context, moduleTarget)) {
      parentSpan.addEvent("Some files were changed during the build. Additional compilation may be required.")
    }
  }
}