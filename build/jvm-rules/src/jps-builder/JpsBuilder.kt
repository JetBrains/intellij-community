// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("HardCodedStringLiteral", "UnstableApiUsage", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceGetOrSet")

package org.jetbrains.bazel.jvm.jps

import com.intellij.openapi.util.io.FileUtilRt
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.apache.arrow.memory.RootAllocator
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.bazel.jvm.ArgMap
import org.jetbrains.bazel.jvm.hashMap
import org.jetbrains.bazel.jvm.jps.impl.BazelBuildDataProvider
import org.jetbrains.bazel.jvm.jps.impl.BazelBuildRootIndex
import org.jetbrains.bazel.jvm.jps.impl.BazelBuildTargetIndex
import org.jetbrains.bazel.jvm.jps.impl.BazelCompileContext
import org.jetbrains.bazel.jvm.jps.impl.BazelCompileScope
import org.jetbrains.bazel.jvm.jps.impl.BazelModuleBuildTarget
import org.jetbrains.bazel.jvm.jps.impl.BazelPathTypeAwareRelativizer
import org.jetbrains.bazel.jvm.jps.impl.JpsTargetBuilder
import org.jetbrains.bazel.jvm.jps.impl.NoopIgnoredFileIndex
import org.jetbrains.bazel.jvm.jps.impl.NoopModuleExcludeIndex
import org.jetbrains.bazel.jvm.jps.impl.RequestLog
import org.jetbrains.bazel.jvm.jps.impl.createPathRelativizer
import org.jetbrains.bazel.jvm.jps.java.BazelJavaBuilder
import org.jetbrains.bazel.jvm.jps.kotlin.IncrementalKotlinBuilder
import org.jetbrains.bazel.jvm.jps.kotlin.NonIncrementalKotlinBuilder
import org.jetbrains.bazel.jvm.jps.output.OutputSink
import org.jetbrains.bazel.jvm.jps.output.createOutputSink
import org.jetbrains.bazel.jvm.jps.output.writeJarAndAbi
import org.jetbrains.bazel.jvm.jps.state.DependencyStateStorage
import org.jetbrains.bazel.jvm.jps.state.LoadSourceFileStateResult
import org.jetbrains.bazel.jvm.jps.state.TargetConfigurationDigestContainer
import org.jetbrains.bazel.jvm.jps.state.TargetConfigurationDigestProperty
import org.jetbrains.bazel.jvm.jps.state.createInitialSourceMap
import org.jetbrains.bazel.jvm.jps.state.saveBuildState
import org.jetbrains.bazel.jvm.kotlin.JvmBuilderFlags
import org.jetbrains.bazel.jvm.kotlin.parseArgs
import org.jetbrains.bazel.jvm.span
import org.jetbrains.bazel.jvm.use
import org.jetbrains.jps.backwardRefs.JavaBackwardReferenceIndexBuilder
import org.jetbrains.jps.builders.logging.BuildLoggingManager
import org.jetbrains.jps.cmdline.ProjectDescriptor
import org.jetbrains.jps.incremental.RebuildRequestedException
import org.jetbrains.jps.incremental.fs.BuildFSState
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService
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
  cachePrefix: String = "",
  forceIncremental: Boolean = false,
): Int {
  val relativizer = createPathRelativizer(baseDir)
  val typeAwareRelativizer = relativizer.typeAwareRelativizer as BazelPathTypeAwareRelativizer
  val log = RequestLog(out = out, parentSpan = parentSpan, tracer = tracer, relativizer = typeAwareRelativizer)

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
    javaFileCount = args.optionalSingle(JvmBuilderFlags.JAVA_COUNT)?.toInt() ?: -1,
  )

  val isIncrementalCompilation = args.boolFlag(JvmBuilderFlags.INCREMENTAL) || forceIncremental
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

  // if output jar doesn't exist, make sure that we do not to use existing cache -
  // set `isRebuild` to true and clear caches in this case
  var isRebuild = validateFileExistence(outJar = outJar, abiJar = abiJar, dataDir = dataDir)
  val buildStateFile = dataDir.resolve("$prefix-state-v1.arrow")
  val depStateStorageFile = dataDir.resolve("$prefix-lib-roots-v2.arrow")
  val classPath = moduleTarget.module.container.getChild(BazelConfigurationHolder.KIND).classPath
  val sourceAndDepState = if (isRebuild) {
    null
  }
  else {
    val state = tracer.span("load and check state") {
      computeBuildState(
        buildStateFile = buildStateFile,
        depStateStorageFile = depStateStorageFile,
        classPath = classPath,
        sourceRelativizer = typeAwareRelativizer.sourceRelativizer,
        allocator = allocator,
        sourceFileToDigest = sourceFileToDigest,
        targetDigests = targetDigests,
        forceIncremental = forceIncremental,
        tracer = tracer,
        log = log,
        parentSpan = it,
      )
    }
    if (state == null) {
      isRebuild = true
      FileUtilRt.deleteRecursively(dataDir)
    }
    state
  }

  val buildState = sourceAndDepState?.first
  val depState = sourceAndDepState?.second
  var exitCode = initAndBuild(
    compileScope = BazelCompileScope(isIncrementalCompilation = true, isRebuild = isRebuild),
    requestLog = log,
    dataDir = dataDir,
    targetDigests = targetDigests,
    moduleTarget = moduleTarget,
    outJar = outJar,
    abiJar = abiJar,
    relativizer = relativizer,
    jpsModel = jpsModel,
    dataManager = BazelBuildDataProvider(
      relativizer = typeAwareRelativizer,
      sourceToDescriptor = buildState?.map ?: createInitialSourceMap(sourceFileToDigest),
      storeFile = buildStateFile,
      allocator = allocator,
      isCleanBuild = isRebuild,
      libRootManager = DependencyStateStorage(
        actualDependencyFileToDigest = dependencyFileToDigest,
        storageFile = depStateStorageFile,
        fileToDigest = depState?.map ?: hashMap(classPath.size),
        classpath = classPath,
      ),
    ),
    buildState = buildState,
    parentSpan = parentSpan,
    isDebugEnabled = isDebugEnabled,
  )

  if (exitCode == -1) {
    log.resetState()
    exitCode = initAndBuild(
      compileScope = BazelCompileScope(isIncrementalCompilation = true, isRebuild = true),
      requestLog = log,
      dataDir = dataDir,
      targetDigests = targetDigests,
      moduleTarget = moduleTarget,
      outJar = outJar,
      abiJar = abiJar,
      relativizer = relativizer,
      jpsModel = jpsModel,
      dataManager = BazelBuildDataProvider(
        relativizer = typeAwareRelativizer,
        sourceToDescriptor = createInitialSourceMap(sourceFileToDigest),
        storeFile = buildStateFile,
        allocator = allocator,
        isCleanBuild = true,
        libRootManager = DependencyStateStorage(
          actualDependencyFileToDigest = dependencyFileToDigest,
          storageFile = depStateStorageFile,
          fileToDigest = hashMap(classPath.size),
          classpath = classPath,
        ),
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
  createOutputSink(oldJar = null, oldAbiJar = null, withAbi = abiJar != null).use { outputSink ->
    val exitCode = tracer.spanBuilder("compile").use { span ->
      val builders = arrayOf(
        createJavaBuilder(
          tracer = tracer,
          isDebugEnabled = isDebugEnabled,
          out = log.out,
          javaFileCount = moduleTarget.javaFileCount,
          isIncremental = false,
        ),
        //NotNullInstrumentingBuilder(),
        NonIncrementalKotlinBuilder(job = coroutineContext.job, span = span),
      ).filterNotNull().toTypedArray()
      builders.sortBy { it.category.ordinal }

      JpsTargetBuilder(
        log = log,
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

private fun createJavaBuilder(
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

private suspend fun initAndBuild(
  compileScope: BazelCompileScope,
  requestLog: RequestLog,
  dataDir: Path,
  targetDigests: TargetConfigurationDigestContainer,
  moduleTarget: BazelModuleBuildTarget,
  outJar: Path,
  abiJar: Path?,
  relativizer: PathRelativizerService,
  jpsModel: JpsModel,
  dataManager: BazelBuildDataProvider,
  buildState: LoadSourceFileStateResult?,
  parentSpan: Span,
  isDebugEnabled: Boolean,
): Int {
  val isRebuild = compileScope.isRebuild
  val tracer = requestLog.tracer
  val storageInitializer = StorageInitializer(dataDir)
  val projectDescriptor = tracer.span("init storage") { span ->
    if (isRebuild) {
      storageInitializer.clearAndInit(span)
    }

    storageInitializer.createProjectDescriptor(
      jpsModel = jpsModel,
      moduleTarget = moduleTarget,
      relativizer = relativizer,
      buildDataProvider = dataManager,
      requestLog = requestLog,
      span = parentSpan,
    )
  }
  try {
    val context = BazelCompileContext(
      scope = compileScope,
      projectDescriptor = projectDescriptor,
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
      oldJar = outJar.takeIf { Files.exists(it) }
      oldAbiJar = if (oldJar == null) null else abiJar?.takeIf { Files.exists(it) }
    }

    createOutputSink(oldJar = oldJar, oldAbiJar = oldAbiJar, withAbi = abiJar != null).use { outputSink ->
      val exitCode = tracer.spanBuilder("compile")
        .setAttribute(AttributeKey.booleanKey("isRebuild"), isRebuild)
        .use { span ->
          val builders = arrayOf(
            IncrementalKotlinBuilder(isRebuild = isRebuild, span = span, dataManager = dataManager, jpsTarget = moduleTarget),
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
            buildDataProvider = dataManager,
            requestLog = requestLog,
            outputSink = outputSink,
            // We remove `outJar` if a rebuild is detected as necessary.
            // Therefore, the `Files.exists` condition is enough.
            // However, it is still better to avoid unnecessary I/O calls.
            parentSpan = parentSpan,
          )
        }
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Throwable) {
        // in case of error during packaging - clear build
        try {
          projectDescriptor.release()
        }
        finally {
          storageInitializer.clearStorage()
        }

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

private val stateFileMetaNames: Array<String> = TargetConfigurationDigestProperty.entries
  .let { entries -> Array(entries.size) { entries.get(it).name } }

private fun CoroutineScope.postBuild(
  moduleTarget: BazelModuleBuildTarget,
  outJar: Path,
  abiJar: Path?,
  context: BazelCompileContext,
  targetDigests: TargetConfigurationDigestContainer,
  buildDataProvider: BazelBuildDataProvider,
  requestLog: RequestLog,
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
      relativizer = buildDataProvider.relativizer.sourceRelativizer,
      metadata = Object2ObjectArrayMap(stateFileMetaNames, targetDigests.asString()),
      allocator = buildDataProvider.allocator,
    )
    buildDataProvider.libRootManager.saveState(buildDataProvider.allocator, buildDataProvider.relativizer.sourceRelativizer)
  }

  if (outputSink.isChanged || context.scope.isRebuild) {
    launch {
      writeJarAndAbi(
        tracer = requestLog.tracer,
        outputSink = outputSink,
        outJar = outJar,
        abiJar = abiJar,
        sourceDescriptors = sourceDescriptors,
      )
    }
  }
  else {
    parentSpan.addEvent("no changes detected, no output JAR will be produced")
  }

  launch(CoroutineName("report build state")) {
    dataManager.reportUnhandledRelativizerPaths()
    if (context.projectDescriptor.fsState.hasUnprocessedChanges(context, moduleTarget)) {
      parentSpan.addEvent("Some files were changed during the build. Additional compilation may be required.")
    }
  }
}