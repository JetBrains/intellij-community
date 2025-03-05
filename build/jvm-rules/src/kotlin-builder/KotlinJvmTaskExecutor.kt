// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm.kotlin

import com.google.devtools.build.lib.view.proto.Deps
import com.google.devtools.build.lib.view.proto.Deps.Dependencies
import kotlinx.coroutines.ensureActive
import org.jetbrains.bazel.jvm.ArgMap
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.messages.PlainTextMessageRenderer
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.Services
import java.io.File
import java.io.Writer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.coroutines.coroutineContext

internal suspend fun compileKotlinForJvm(
  args: ArgMap<JvmBuilderFlags>,
  context: TraceHelper,
  sources: List<Path>,
  out: Writer,
  baseDir: Path,
  info: CompilationTaskInfo,
): Int {
  val kotlinArgs = K2JVMCompilerArguments()
  configureCommonCompilerArgs(kotlinArgs, args, baseDir)

  val classPath = createClasspath(args, baseDir)
  kotlinArgs.classpath = classPath.joinToString(File.pathSeparator) { it.toString() }

  kotlinArgs.moduleName = info.moduleName
  val outFile = baseDir.resolve(args.mandatorySingle(JvmBuilderFlags.OUT))
  val outFilePath = outFile.toString()
  kotlinArgs.destination = outFilePath

  fun printOptions() {
    wrapOutput(out, info.label, classifier = "K2JVM Compiler Arguments") {
      out.appendLine("pluginConfigurations:\n  ${getDebugInfoAboutPlugins(args, baseDir, info.label)}")
      out.appendLine(toArgumentStrings(kotlinArgs).joinToString("\n"))
    }
  }

  if (context.isTracing) {
    printOptions()
  }

  require(kotlinArgs.freeArgs.isEmpty())
  kotlinArgs.freeArgs = sources.map { it.toString() }

  val config = prepareCompilerConfiguration(args = args, kotlinArgs = kotlinArgs, baseDir = baseDir)

  val messageCollector = WriterBackedMessageCollector(verbose = context.isTracing)
  config.put(CLIConfigurationKeys.ORIGINAL_MESSAGE_COLLECTOR_KEY, messageCollector)
  config.put(JVMConfigurationKeys.OUTPUT_JAR, outFile.toFile())
  configureModule(
    moduleName = info.moduleName,
    config = config,
    outFileOrDirPath = outFilePath,
    args = args,
    baseDir = baseDir,
    allSources = sources,
    changedKotlinSources = null,
    classPath = classPath,
  )

  coroutineContext.ensureActive()
  val pipeline = createJvmPipeline(config) {
    // todo write
  }
  var code = pipeline.execute(kotlinArgs, Services.EMPTY, messageCollector).code
  if (messageCollector.hasErrors()) {
    code = ExitCode.COMPILATION_ERROR.code
  }
  if (code == 0 && !context.isTracing) {
    return 0
  }

  if (!context.isTracing) {
    printOptions()
  }

  val renderer = object : PlainTextMessageRenderer(/* colorEnabled = */ true) {
    override fun getPath(location: CompilerMessageSourceLocation): String {
      return baseDir.relativize(Path.of(location.path)).toString()
    }

    override fun getName(): String = "RelativePath"
  }
  wrapOutput(out, info.label, classifier = "K2JVM Compiler Messages") {
    for (entry in messageCollector.entries) {
      out.appendLine(renderer.render(entry.severity, entry.message, entry.location))
    }
  }
  return code
}

private inline fun wrapOutput(out: Writer, label: String, classifier: String, task: () -> Unit) {
  out.appendLine("\u001B[1m=============== $classifier ($label) ===============\u001B[0m")
  task()
  out.appendLine("\u001B[1m=============== END of $classifier ($label) ===============\u001B[0m")
}

private fun createClasspath(args: ArgMap<JvmBuilderFlags>, baseDir: Path): List<Path> {
  if (!args.boolFlag(JvmBuilderFlags.REDUCED_CLASSPATH_MODE)) {
    return args.mandatory(JvmBuilderFlags.CP).map { baseDir.resolve(it).normalize() }
  }

  val directDependencies = args.mandatory(JvmBuilderFlags.DIRECT_DEPENDENCIES)

  val depsArtifacts = args.optional(JvmBuilderFlags.DEPS_ARTIFACTS) ?: return directDependencies.map { baseDir.resolve(it).normalize() }
  val transitiveDepsForCompile = LinkedHashSet<String>()
  for (jdepsPath in depsArtifacts) {
    Files.newInputStream(Path.of(jdepsPath)).use {
      val deps = Dependencies.parseFrom(it)
      for (dep in deps.dependencyList) {
        if (dep.kind == Deps.Dependency.Kind.EXPLICIT) {
          transitiveDepsForCompile.add(dep.path)
        }
      }
    }
  }

  return (directDependencies.asSequence() + transitiveDepsForCompile).map { baseDir.resolve(it).normalize() }.toList()
}

private data class LogMessage(
  @JvmField val severity: CompilerMessageSeverity,
  @JvmField val message: String,
  @JvmField val location: CompilerMessageSourceLocation?,
)

private class WriterBackedMessageCollector(
  private val verbose: Boolean,
) : MessageCollector {
  private var hasErrors = false

  @JvmField
  val entries = mutableListOf<LogMessage>()

  override fun clear() {
  }

  override fun report(
    severity: CompilerMessageSeverity,
    message: String,
    location: CompilerMessageSourceLocation?
  ) {
    if (!verbose && CompilerMessageSeverity.Companion.VERBOSE.contains(severity)) {
      return
    }

    hasErrors = hasErrors or severity.isError
    synchronized(this) {
      entries.add(LogMessage(severity = severity, message = message, location = location))
    }
  }

  override fun hasErrors(): Boolean = hasErrors
}