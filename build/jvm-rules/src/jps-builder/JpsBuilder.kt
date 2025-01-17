// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("HardCodedStringLiteral", "UnstableApiUsage", "ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.bazel.jvm.jps

import com.intellij.openapi.diagnostic.IdeaLogRecordFormatter
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtilRt
import kotlinx.coroutines.*
import org.apache.arrow.memory.RootAllocator
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.bazel.jvm.WorkRequest
import org.jetbrains.bazel.jvm.WorkRequestExecutor
import org.jetbrains.bazel.jvm.jps.impl.*
import org.jetbrains.bazel.jvm.jps.state.TargetConfigurationDigestContainer
import org.jetbrains.bazel.jvm.jps.state.saveTargetState
import org.jetbrains.bazel.jvm.kotlin.ArgMap
import org.jetbrains.bazel.jvm.kotlin.JvmBuilderFlags
import org.jetbrains.bazel.jvm.kotlin.parseArgs
import org.jetbrains.bazel.jvm.logging.LogWriter
import org.jetbrains.bazel.jvm.processRequests
import org.jetbrains.jps.api.CanceledStatus
import org.jetbrains.jps.api.GlobalOptions
import org.jetbrains.jps.backwardRefs.JavaBackwardReferenceIndexBuilder
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType
import org.jetbrains.jps.incremental.CompileContextImpl
import org.jetbrains.jps.incremental.CompileScopeImpl
import org.jetbrains.jps.incremental.ModuleBuildTarget
import org.jetbrains.jps.incremental.RebuildRequestedException
import org.jetbrains.jps.incremental.java.JavaBuilder
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService
import org.jetbrains.jps.incremental.storage.StorageManager
import org.jetbrains.jps.model.JpsModel
import org.jetbrains.kotlin.config.IncrementalCompilation
import org.jetbrains.kotlin.jps.incremental.KotlinCompilerReferenceIndexBuilder
import java.io.Writer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.coroutines.coroutineContext

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

fun configureGlobalJps(logWriter: LogWriter) {
  Logger.setFactory { BazelLogger(category = IdeaLogRecordFormatter.smartAbbreviate(it), writer = logWriter) }
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

class JpsBuildWorker private constructor(private val allocator: RootAllocator) : WorkRequestExecutor {
  companion object {
    @JvmStatic
    fun main(startupArgs: Array<String>) {
      RootAllocator(Long.MAX_VALUE).use { allocator ->
        processRequests(
          startupArgs = startupArgs,
          executor = JpsBuildWorker(allocator),
          setup = { configureGlobalJps(it) },
        )
      }
    }
  }

  override suspend fun execute(request: WorkRequest, writer: Writer, baseDir: Path): Int {
    val dependencyFileToDigest = HashMap<Path, ByteArray>()
    val sourceFileToDigest = HashMap<Path, ByteArray>(request.inputs.size)
    val sources = ArrayList<Path>()
    for (input in request.inputs) {
      if (input.path.endsWith(".kt") || input.path.endsWith(".java")) {
        val file = baseDir.resolve(input.path).normalize()
        sources.add(file)
        sourceFileToDigest.put(file, input.digest)
      }
      else if (input.path.endsWith(".jar")) {
        dependencyFileToDigest.put(baseDir.resolve(input.path).normalize(), input.digest)
      }
    }

    return buildUsingJps(
      baseDir = baseDir,
      args = parseArgs(request.arguments),
      out = writer,
      sources = sources,
      dependencyFileToDigest = dependencyFileToDigest,
      isDebugEnabled = request.verbosity > 0,
      sourceFileToDigest = sourceFileToDigest,
      allocator = allocator,
    )
  }
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
): Int {
  val log = RequestLog(out = out, isDebugEnabled = isDebugEnabled)

  val abiJar = args.optionalSingle(JvmBuilderFlags.ABI_OUT)?.let { baseDir.resolve(it).normalize() }
  val outJar = baseDir.resolve(args.mandatorySingle(JvmBuilderFlags.OUT)).normalize()
  val prefix = outJar.fileName.toString().removeSuffix(".jar")
  val bazelOutDir = outJar.parent
  val dataDir = bazelOutDir.resolve("$prefix-jps-data")

  // incremental compilation - we do not clear dir
  val classOutDir = bazelOutDir.resolve("$prefix-classes")

  val (jpsModel, targetDigests) = loadJpsModel(
    sources = sources,
    args = args,
    classPathRootDir = baseDir,
    classOutDir = classOutDir,
    dependencyFileToDigest = dependencyFileToDigest
  )
  val moduleTarget = BazelModuleBuildTarget(
    outDir = classOutDir,
    module = jpsModel.project.modules.single(),
    sources = sources,
  )

  val relativizer = createPathRelativizer(baseDir = baseDir, classOutDir = classOutDir)

  // if class output dir doesn't exist, make sure that we do not to use existing cache - pass `isRebuild` as true in this case
  var isRebuild = Files.notExists(classOutDir)

  val buildStateFile = dataDir.resolve("$prefix-state-v1.arrow")
  val typeAwareRelativizer = relativizer.typeAwareRelativizer!!
  val buildState = loadBuildState(buildStateFile, typeAwareRelativizer, allocator, log)
  if (buildState == null) {
    FileUtilRt.deleteRecursively(dataDir)
    FileUtilRt.deleteRecursively(classOutDir)

    isRebuild = true
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
      actualDigestMap = sourceFileToDigest,
      sourceToDescriptor = buildState ?: HashMap(sourceFileToDigest.size),
      storeFile = buildStateFile,
      allocator = allocator,
      isCleanBuild = isRebuild,
    ),
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
        actualDigestMap = sourceFileToDigest,
        sourceToDescriptor = HashMap(sourceFileToDigest.size),
        storeFile = buildStateFile,
        allocator = allocator,
        isCleanBuild = true,
      ),
    )
  }

  return exitCode
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
): Int {
  if (messageHandler.isDebugEnabled) {
    messageHandler.info("build (isRebuild=$isRebuild)")
  }

  val storageInitializer = StorageInitializer(dataDir = dataDir, classOutDir = classOutDir)
  val storageManager = if (isRebuild) {
    storageInitializer.clearAndInit(messageHandler)
  }
  else {
    storageInitializer.init(messageHandler, targetDigests)
  }

  try {
    val projectDescriptor = storageInitializer.createProjectDescriptor(
      messageHandler = messageHandler,
      jpsModel = jpsModel,
      moduleTarget = moduleTarget,
      relativizer = relativizer,
      buildDataProvider = buildDataProvider,
    )
    try {
      val compileScope = CompileScopeImpl(
        /* types = */ java.util.Set.of(JavaModuleBuildTargetType.PRODUCTION),
        /* typesToForceBuild = */ java.util.Set.of(),
        /* targets = */ if (isRebuild) java.util.Set.of(moduleTarget) else java.util.Set.of(),
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

      val builders = arrayOf(
        JavaBuilder(BazelSharedThreadPool),
        //NotNullInstrumentingBuilder(),
        JavaBackwardReferenceIndexBuilder(),
        BazelKotlinBuilder(isKotlinBuilderInDumbMode = false, log = messageHandler, dataManager = buildDataProvider),
        KotlinCompilerReferenceIndexBuilder(),
      )
      builders.sortBy { it.category.ordinal }
      val exitCode = JpsTargetBuilder(
        log = messageHandler,
        isCleanBuild = storageInitializer.isCleanBuild,
        dataManager = buildDataProvider,
      ).build(context = context, moduleTarget = moduleTarget, builders = builders)
      if (exitCode == 0) {
        try {
          postBuild(
            messageHandler = messageHandler,
            moduleTarget = moduleTarget,
            outJar = outJar,
            abiJar = abiJar,
            classOutDir = classOutDir,
            context = context,
            targetDigests = targetDigests,
            storageManager = storageManager,
            buildDataProvider = buildDataProvider,
          )
        }
        catch (e: Throwable) {
          // in case of any error during packaging - clear build
          storageManager.forceClose()
          projectDescriptor.release()

          storageInitializer.clearStorage()

          throw e
        }
      }
      return exitCode
    }
    catch (e: RebuildRequestedException) {
      messageHandler.info("RebuildRequestedException: ${e.cause?.message}: ${e.stackTraceToString()}")
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

private suspend fun postBuild(
  messageHandler: RequestLog,
  moduleTarget: ModuleBuildTarget,
  outJar: Path,
  abiJar: Path?,
  classOutDir: Path,
  context: CompileContextImpl,
  targetDigests: TargetConfigurationDigestContainer,
  storageManager: StorageManager,
  buildDataProvider: BazelBuildDataProvider,
) {
  coroutineScope {
    val dataManager = context.projectDescriptor.dataManager
    val sourceDescriptors = buildDataProvider.getFinalList()
    launch(CoroutineName("save caches")) {
      // save config state only in the end
      saveTargetState(
        targetDigests = targetDigests,
        manager = context.projectDescriptor.dataManager.targetStateManager as BazelBuildTargetStateManager,
        storageManager = storageManager,
      )

      dataManager.flush(/* memoryCachesOnly = */ false)

      ensureActive()

      saveBuildState(
        buildStateFile = buildDataProvider.storeFile,
        list = sourceDescriptors,
        relativizer = buildDataProvider.relativizer,
        allocator = buildDataProvider.allocator,
      )
    }

    launch {
      // deletes class loader classpath index files for changed output roots
      // todo remove when we will produce JAR directly
      Files.deleteIfExists(classOutDir.resolve("classpath.index"))
      Files.deleteIfExists(classOutDir.resolve(".unmodified"))
    }

    launch(CoroutineName("create output JAR and ABI JAR")) {
      // pack to jar
      messageHandler.measureTime("pack and abi") {
        packageToJar(
          outJar = outJar,
          abiJar = abiJar,
          sourceDescriptors = sourceDescriptors,
          classOutDir = classOutDir,
          log = messageHandler,
        )
      }
    }

    launch(CoroutineName("report build state")) {
      dataManager.reportUnhandledRelativizerPaths()
      reportRebuiltModules(context)
      reportUnprocessedChanges(context, moduleTarget)
    }
  }
}