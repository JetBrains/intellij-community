// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("HardCodedStringLiteral", "UnstableApiUsage")

package org.jetbrains.bazel.jvm.jps

import com.google.devtools.build.lib.worker.WorkerProtocol
import org.jetbrains.bazel.jvm.WorkRequestExecutor
import org.jetbrains.bazel.jvm.kotlin.ArgMap
import org.jetbrains.bazel.jvm.kotlin.JvmBuilderFlags
import org.jetbrains.bazel.jvm.kotlin.parseArgs
import org.jetbrains.bazel.jvm.processRequests
import org.jetbrains.jps.api.CanceledStatus
import org.jetbrains.jps.api.GlobalOptions
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType
import org.jetbrains.jps.cmdline.BuildRunner
import org.jetbrains.jps.cmdline.ProjectDescriptor
import org.jetbrains.jps.incremental.*
import org.jetbrains.jps.incremental.fs.BuildFSState
import org.jetbrains.jps.incremental.messages.BuildMessage
import org.jetbrains.jps.incremental.messages.CompilerMessage
import org.jetbrains.kotlin.config.IncrementalCompilation
import java.io.PrintWriter
import java.io.Writer
import java.nio.file.Path

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

internal fun buildUsingJps(
  workingDir: Path,
  args: ArgMap<JvmBuilderFlags>,
  out: Writer,
  sources: List<Path>,
  classPathRootDir: Path = workingDir,
): Int {

  val messageHandler = ConsoleMessageHandler(out)

  // todo - do we need LowMemoryWatcherManager?


  val outJar = workingDir.resolve(args.mandatorySingle(JvmBuilderFlags.OUTPUT)).normalize()
  val prefix = outJar.fileName.toString().removeSuffix(".jar")
  val bazelOutDir = outJar.parent
  val dataStorageRoot = bazelOutDir.resolve("$prefix-jps-data")

  // incremental compilation - we do not clear dir
  val classOutDir = bazelOutDir.resolve("$prefix-classes")

  val buildRunner = BuildRunner(BazelJpsModelLoader(sources = sources, args = args, classPathRootDir = classPathRootDir, classOutDir = classOutDir))
  val projectDescriptor = buildRunner.load(messageHandler, dataStorageRoot, BuildFSState(/* alwaysScanFS = */ true))
  try {
    runBuild(
      projectDescriptor = projectDescriptor,
      canceledStatus = CanceledStatus.NULL,
      messageHandler = messageHandler,
      forceClean = false,
      out = out,
    )
  }
  finally {
    projectDescriptor.release()
  }

  return if (messageHandler.hasErrors()) 1 else 0
}

@Suppress("SameParameterValue")
private fun runBuild(
  projectDescriptor: ProjectDescriptor,
  canceledStatus: CanceledStatus,
  messageHandler: MessageHandler,
  forceClean: Boolean,
  out: Writer,
) {
  var attempt = 0
  var forceCleanCaches = forceClean
  val targetType = JavaModuleBuildTargetType.PRODUCTION
  while (attempt < 2 && !canceledStatus.isCanceled) {
    val targetTypes = setOf(targetType)
    val targetTypesToForceBuild = if (forceClean) setOf(targetType) else emptySet()
    val compileScope = CompileScopeImpl(
      /* types = */ targetTypes,
      /* typesToForceBuild = */ targetTypesToForceBuild,
      /* targets = */ emptySet(),
      /* files = */ emptyMap()
    )
    val builder = IncProjectBuilder(
      /* pd = */ projectDescriptor,
      /* builderRegistry = */ BuilderRegistry.getInstance(),
      /* builderParams = */ emptyMap(),
      /* cs = */ canceledStatus,
      /* isTestMode = */ false,
    )
    builder.addMessageHandler(messageHandler)
    try {
      builder.build(compileScope, forceClean)
      break
    }
    catch (e: RebuildRequestedException) {
      if (attempt == 0) {
        PrintWriter(out).use {
          e.printStackTrace(it)
        }
        forceCleanCaches = true
      }
      else {
        throw e
      }
    }
    attempt++
  }
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