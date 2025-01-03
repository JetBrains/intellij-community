// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("HardCodedStringLiteral", "UnstableApiUsage", "ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.bazel.jvm.jps

import com.google.devtools.build.lib.worker.WorkerProtocol
import com.intellij.openapi.util.io.FileUtilRt
import org.jetbrains.bazel.jvm.WorkRequestExecutor
import org.jetbrains.bazel.jvm.jps.impl.BazelModuleExcludeIndex
import org.jetbrains.bazel.jvm.kotlin.ArgMap
import org.jetbrains.bazel.jvm.kotlin.JvmBuilderFlags
import org.jetbrains.bazel.jvm.kotlin.parseArgs
import org.jetbrains.bazel.jvm.processRequests
import org.jetbrains.jps.api.GlobalOptions
import org.jetbrains.jps.backwardRefs.JavaBackwardReferenceIndexWriter
import org.jetbrains.jps.builders.impl.BuildDataPathsImpl
import org.jetbrains.jps.builders.impl.BuildRootIndexImpl
import org.jetbrains.jps.builders.impl.BuildTargetIndexImpl
import org.jetbrains.jps.builders.impl.BuildTargetRegistryImpl
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType
import org.jetbrains.jps.builders.logging.BuildLoggingManager
import org.jetbrains.jps.cmdline.ProjectDescriptor
import org.jetbrains.jps.incremental.BuilderRegistry
import org.jetbrains.jps.incremental.CompileScopeImpl
import org.jetbrains.jps.incremental.MessageHandler
import org.jetbrains.jps.incremental.RebuildRequestedException
import org.jetbrains.jps.incremental.fs.BuildFSState
import org.jetbrains.jps.incremental.messages.BuildMessage
import org.jetbrains.jps.incremental.messages.CompilerMessage
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService
import org.jetbrains.jps.incremental.storage.BuildDataManager
import org.jetbrains.jps.incremental.storage.BuildTargetsState
import org.jetbrains.jps.incremental.storage.StorageManager
import org.jetbrains.jps.indices.impl.IgnoredFileIndexImpl
import org.jetbrains.jps.model.JpsModel
import org.jetbrains.kotlin.config.IncrementalCompilation
import java.io.Writer
import java.nio.file.Path
import java.util.Map
import java.util.Set

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

  override suspend fun execute(request: WorkerProtocol.WorkRequest, writer: Writer, baseDir: Path): Int {
    val sources = request.inputsList.asSequence()
      .filter { it.path.endsWith(".kt") || it.path.endsWith(".java") }
      .map { baseDir.resolve(it.path) }
      .toList()
    return buildUsingJps(workingDir = baseDir, args = parseArgs(request.argumentsList), out = writer, sources = sources)
  }
}

internal suspend fun buildUsingJps(
  workingDir: Path,
  args: ArgMap<JvmBuilderFlags>,
  out: Writer,
  sources: List<Path>,
  classPathRootDir: Path = workingDir,
): Int {

  val messageHandler = ConsoleMessageHandler(out)

  val outJar = workingDir.resolve(args.mandatorySingle(JvmBuilderFlags.OUTPUT)).normalize()
  val prefix = outJar.fileName.toString().removeSuffix(".jar")
  val bazelOutDir = outJar.parent
  val dataStorageRoot = bazelOutDir.resolve("$prefix-jps-data")

  // incremental compilation - we do not clear dir
  val classOutDir = bazelOutDir.resolve("$prefix-classes")

  val buildRunner = BuildRunner()
  val jpsModel = loadJpsModel(sources = sources, args = args, classPathRootDir = classPathRootDir, classOutDir = classOutDir)

  fun createProjectDescriptor(): ProjectDescriptor? {
    return buildRunner.load(
      messageHandler = messageHandler,
      dataStorageRoot = dataStorageRoot,
      fsState = BuildFSState(/* alwaysScanFS = */ true),
      jpsModel = jpsModel,
    )
  }

  fun clearStorage() {
    // todo rename and store
    FileUtilRt.deleteRecursively(dataStorageRoot)
    FileUtilRt.deleteRecursively(classOutDir)
  }

  try {
    var projectDescriptor: ProjectDescriptor? = null
    try {
      projectDescriptor = createProjectDescriptor()
      var checkRebuildRequired = true
      if (projectDescriptor == null) {
        clearStorage()
        checkRebuildRequired = false

        projectDescriptor = requireNotNull(createProjectDescriptor()) {
          "The storage has been corrupted twice in a row, resulting in an unrecoverable error"
        }
      }

      doBuild(checkRebuildRequired = checkRebuildRequired, projectDescriptor = projectDescriptor, messageHandler = messageHandler)
    }
    finally {
      projectDescriptor?.release()
    }
  }
  catch (_: RebuildRequestedException) {
    clearStorage()

    val projectDescriptor = requireNotNull(createProjectDescriptor()) {
      "Unrecoverable error"
    }

    try {
      doBuild(checkRebuildRequired = false, projectDescriptor = projectDescriptor, messageHandler = messageHandler)
    }
    finally {
      projectDescriptor.release()
    }
  }

  return if (messageHandler.hasErrors()) 1 else 0
}

private suspend fun doBuild(
  checkRebuildRequired: Boolean,
  projectDescriptor: ProjectDescriptor,
  messageHandler: MessageHandler,
) {
  val compileScope = CompileScopeImpl(
    /* types = */ Set.of(JavaModuleBuildTargetType.PRODUCTION),
    /* typesToForceBuild = */ Set.of(),
    /* targets = */ Set.of(),
    /* files = */ Map.of()
  )

  val builder = JpsProjectBuilder(
    projectDescriptor = projectDescriptor,
    builderRegistry = BuilderRegistry.getInstance(),
    builderParams = emptyMap(),
    isTestMode = false,
    messageHandler = messageHandler,
  )
  if (checkRebuildRequired) {
    builder.checkRebuildRequired(compileScope)
  }
  builder.build(compileScope)
}

private class ConsoleMessageHandler(private val out: Writer) : MessageHandler {
  private var hasErrors = false

  override fun processMessage(message: BuildMessage) {
    val messageText = when (message) {
      is CompilerMessage -> {
        when {
          message.sourcePath == null -> message.messageText
          message.line < 0 -> message.sourcePath + ": " + message.messageText
          else -> message.sourcePath + "(" + message.line + ":" + message.column + "): " + message.messageText
        }
      }

      else -> message.messageText
    }

    if (messageText.isEmpty()) {
      return
    }

    if (message.kind == BuildMessage.Kind.ERROR) {
      out.appendLine("Error: $messageText")
      hasErrors = true
    }
    else if (message.kind !== BuildMessage.Kind.PROGRESS || !messageText.startsWith("Compiled") && !messageText.startsWith("Copying")) {
      out.appendLine(messageText)
    }
  }

  fun hasErrors(): Boolean = hasErrors
}

private fun createStorageManager(dataStorageRoot: Path): StorageManager {
  val manager = StorageManager(dataStorageRoot.resolve("jps-portable-cache.db"))
  manager.open()
  return manager
}

private class BuildRunner() {
  fun load(messageHandler: MessageHandler, dataStorageRoot: Path, fsState: BuildFSState, jpsModel: JpsModel): ProjectDescriptor? {
    val dataPaths = BuildDataPathsImpl(dataStorageRoot.toFile())
    val targetRegistry = BuildTargetRegistryImpl(jpsModel)
    val moduleExcludeIndex = BazelModuleExcludeIndex
    val ignoredFileIndex = IgnoredFileIndexImpl(jpsModel)
    val buildRootIndex = BuildRootIndexImpl(targetRegistry, jpsModel, moduleExcludeIndex, dataPaths, ignoredFileIndex)
    val targetIndex = BuildTargetIndexImpl(targetRegistry, buildRootIndex)

    val relativizer = PathRelativizerService(jpsModel.project, JavaBackwardReferenceIndexWriter.isCompilerReferenceFSCaseSensitive())

    val storageManager = createStorageManager(dataStorageRoot)
    var dataManager: BuildDataManager? = null
    try {
      @Suppress("DEPRECATION")
      dataManager = BuildDataManager(
        dataPaths,
        BuildTargetsState(dataPaths, jpsModel, buildRootIndex),
        relativizer,
        storageManager,
      )
      if (dataManager.versionDiffers()) {
        storageManager.forceClose()

        messageHandler.processMessage(
          CompilerMessage("", BuildMessage.Kind.INFO, "Dependency data format has changed, project rebuild required"),
        )
        return null
      }
    }
    catch (e: Exception) {
      messageHandler.processMessage(
        CompilerMessage("", BuildMessage.Kind.INTERNAL_BUILDER_ERROR, "Cannot open cache storage: ${e.stackTraceToString()}"),
      )

      storageManager.forceClose()
      dataManager?.close()

      return null
    }

    return ProjectDescriptor(
      jpsModel, fsState, dataManager, BuildLoggingManager.DEFAULT, moduleExcludeIndex, targetIndex, buildRootIndex, ignoredFileIndex
    )
  }
}