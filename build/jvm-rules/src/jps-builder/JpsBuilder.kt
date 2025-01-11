// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("HardCodedStringLiteral", "UnstableApiUsage", "ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.bazel.jvm.jps

import com.intellij.openapi.diagnostic.IdeaLogRecordFormatter
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.bazel.jvm.WorkRequest
import org.jetbrains.bazel.jvm.WorkRequestExecutor
import org.jetbrains.bazel.jvm.jps.impl.*
import org.jetbrains.bazel.jvm.jps.state.TargetConfigurationDigestContainer
import org.jetbrains.bazel.jvm.jps.state.saveTargetState
import org.jetbrains.bazel.jvm.kotlin.ArgMap
import org.jetbrains.bazel.jvm.kotlin.JvmBuilderFlags
import org.jetbrains.bazel.jvm.kotlin.parseArgs
import org.jetbrains.bazel.jvm.processRequests
import org.jetbrains.jps.api.CanceledStatus
import org.jetbrains.jps.api.GlobalOptions
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType
import org.jetbrains.jps.incremental.*
import org.jetbrains.jps.incremental.storage.ExperimentalSourceToOutputMapping
import org.jetbrains.jps.incremental.storage.StorageManager
import org.jetbrains.kotlin.config.IncrementalCompilation
import java.io.Writer
import java.nio.file.Files
import java.nio.file.Path
import java.util.Set
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

internal fun configureGlobalJps() {
  Logger.setFactory { BazelLogger(IdeaLogRecordFormatter.smartAbbreviate(it)) }
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

object JpsBuildWorker : WorkRequestExecutor {
  @JvmStatic
  fun main(startupArgs: Array<String>) {
    configureGlobalJps()
    processRequests(startupArgs, this)
  }

  override suspend fun execute(request: WorkRequest, writer: Writer, baseDir: Path): Int {
    val dependencyFileToDigest = HashMap<Path, ByteArray>()
    val sources = ArrayList<Path>()
    for (input in request.inputs) {
      if (input.path.endsWith(".kt") || input.path.endsWith(".java")) {
        sources.add(baseDir.resolve(input.path).normalize())
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
    )
  }
}

internal suspend fun buildUsingJps(
  baseDir: Path,
  args: ArgMap<JvmBuilderFlags>,
  out: Writer,
  sources: List<Path>,
  dependencyFileToDigest: Map<Path, ByteArray>,
  isDebugEnabled: Boolean,
): Int {
  val messageHandler = ConsoleMessageHandler(out)

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
    dependencyFileToDigest = dependencyFileToDigest,
  )
  val moduleTarget = BazelModuleBuildTarget(
    outDir = classOutDir,
    module = jpsModel.project.modules.single(),
    sources = sources,
  )

  val relativizer = createPathRelativizer(baseDir = baseDir, classOutDir = classOutDir)

  val compileScope = CompileScopeImpl(
    /* types = */ Set.of(JavaModuleBuildTargetType.PRODUCTION),
    /* typesToForceBuild = */ Set.of(),
    /* targets = */ Set.of(),
    /* files = */ java.util.Map.of()
  )

  suspend fun initAndBuild(isRebuild: Boolean): Boolean {
    val storageInitializer = StorageInitializer(dataDir = dataDir, classOutDir = classOutDir)
    val storageManager = if (isRebuild) {
      storageInitializer.clearAndInit(messageHandler)
    }
    else {
      storageInitializer.init(messageHandler, targetDigests)
    }

    try {
      val projectDescriptor = storageInitializer.createProjectDescriptor(messageHandler, jpsModel, moduleTarget, relativizer)
      try {
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
        JpsProjectBuilder(
          builderRegistry = BuilderRegistry.getInstance(),
          messageHandler = messageHandler,
          isCleanBuild = storageInitializer.isCleanBuild,
        ).build(context, moduleTarget)
        postBuild(
          messageHandler = messageHandler,
          moduleTarget = moduleTarget,
          outJar = outJar,
          abiJar = abiJar,
          classOutDir = classOutDir,
          context = context,
          targetDigests = targetDigests,
          storageManager = storageManager,
        )
        return true
      }
      finally {
        projectDescriptor.release()
      }
    }
    catch (_: RebuildRequestedException) {
      return false
    }
    finally {
      storageManager.forceClose()
    }
  }

  // if class output dir doesn't exist, make sure that we do not to use existing cache - pass `isRebuild` as true in this case
  if (!initAndBuild(isRebuild = Files.notExists(classOutDir))) {
    initAndBuild(isRebuild = true)
  }

  return if (messageHandler.hasErrors()) 1 else 0
}


private suspend fun postBuild(
  messageHandler: ConsoleMessageHandler,
  moduleTarget: ModuleBuildTarget,
  outJar: Path,
  abiJar: Path?,
  classOutDir: Path,
  context: CompileContextImpl,
  targetDigests: TargetConfigurationDigestContainer,
  storageManager: StorageManager,
) {
  coroutineScope {
    val dataManager = context.projectDescriptor.dataManager
    launch(CoroutineName("save caches")) {
      // save config state only in the end
      saveTargetState(
        targetDigests = targetDigests,
        manager = context.projectDescriptor.dataManager.targetStateManager as BazelBuildTargetStateManager,
        storageManager = storageManager,
      )

      dataManager.flush(/* memoryCachesOnly = */ false)
    }

    launch(CoroutineName("create output JAR and ABI JAR")) {
      // pack to jar
      messageHandler.measureTime("pack and abi") {
        val sourceToOutputMap = dataManager.getSourceToOutputMap(moduleTarget) as ExperimentalSourceToOutputMapping
        packageToJar(
          outJar = outJar,
          abiJar = abiJar,
          sourceToOutputMap = sourceToOutputMap,
          classOutDir = classOutDir,
          messageHandler = messageHandler,
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