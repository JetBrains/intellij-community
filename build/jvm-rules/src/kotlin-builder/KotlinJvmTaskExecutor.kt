// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm.kotlin

import com.google.devtools.build.lib.view.proto.Deps
import com.google.devtools.build.lib.view.proto.Deps.Dependencies
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.ensureActive
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.*
import org.jetbrains.kotlin.cli.common.setupCommonArguments
import org.jetbrains.kotlin.cli.jvm.compiler.configurePlugins
import org.jetbrains.kotlin.cli.jvm.compiler.k2jvm
import org.jetbrains.kotlin.cli.jvm.compiler.loadPlugins
import org.jetbrains.kotlin.cli.jvm.setupJvmSpecificArguments
import org.jetbrains.kotlin.config.messageCollector
import org.jetbrains.kotlin.metadata.deserialization.MetadataVersion
import java.io.BufferedInputStream
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
  workingDir: Path,
  info: CompilationTaskInfo,
): Int {
  val kotlinArgs = K2JVMCompilerArguments()
  configureCommonCompilerArgs(kotlinArgs, args, workingDir)

  kotlinArgs.classpath = createClasspath(args, workingDir)

  kotlinArgs.moduleName = info.moduleName
  kotlinArgs.destination = workingDir.resolve(args.mandatorySingle(JvmBuilderFlags.OUT)).toString()

  val pluginConfigurations = configurePlugins(args = args, workingDir = workingDir, label = info.label)

  fun printOptions() {
    wrapOutput(out, info.label, classifier = "K2JVM Compiler Arguments") {
      out.appendLine("pluginConfigurations:\n  ${pluginConfigurations.joinToString("\n  ") { it.toString() }}")
      out.appendLine(toArgumentStrings(kotlinArgs).joinToString("\n"))
    }
  }

  if (context.isTracing) {
    printOptions()
  }

  require(kotlinArgs.freeArgs.isEmpty())
  kotlinArgs.freeArgs = sources.map { it.toString() }
  val messageCollector = WriterBackedMessageCollector(verbose = context.isTracing)

  val config = org.jetbrains.kotlin.cli.jvm.compiler.configTemplate.copy()
  config.put(CLIConfigurationKeys.ORIGINAL_MESSAGE_COLLECTOR_KEY, messageCollector)
  val collector = GroupingMessageCollector(messageCollector, kotlinArgs.allWarningsAsErrors, kotlinArgs.reportAllWarnings).also {
    config.messageCollector = it
  }
  config.setupCommonArguments(kotlinArgs) { version -> MetadataVersion(*version) }
  config.setupJvmSpecificArguments(kotlinArgs)
  if (args.boolFlag(JvmBuilderFlags.ALLOW_KOTLIN_PACKAGE)) {
    config.put(CLIConfigurationKeys.ALLOW_KOTLIN_PACKAGE, true)
  }

  coroutineContext.ensureActive()

  val rootDisposable = Disposer.newDisposable("Disposable for Bazel Kotlin Compiler")
  var code = try {
    loadPlugins(configuration = config, pluginConfigurations = pluginConfigurations)
    k2jvm(args = kotlinArgs, config = config, rootDisposable = rootDisposable).code
  }
  finally {
    Disposer.dispose(rootDisposable)
    collector.flush()
  }

  if (collector.hasErrors()) {
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
      return workingDir.relativize(Path.of(location.path)).toString()
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

private fun createClasspath(args: ArgMap<JvmBuilderFlags>, baseDir: Path): String {
  if (!args.boolFlag(JvmBuilderFlags.REDUCED_CLASSPATH_MODE)) {
    return args.mandatory(JvmBuilderFlags.CLASSPATH).joinToString(File.pathSeparator) { baseDir.resolve(it).normalize().toString() }
  }

  val directDependencies = args.mandatory(JvmBuilderFlags.DIRECT_DEPENDENCIES)

  val depsArtifacts = args.optional(JvmBuilderFlags.DEPS_ARTIFACTS) ?: return directDependencies.joinToString(File.pathSeparator)
  val transitiveDepsForCompile = LinkedHashSet<String>()
  for (jdepsPath in depsArtifacts) {
    BufferedInputStream(Files.newInputStream(Path.of(jdepsPath))).use {
      val deps = Dependencies.parseFrom(it)
      for (dep in deps.dependencyList) {
        if (dep.kind == Deps.Dependency.Kind.EXPLICIT) {
          transitiveDepsForCompile.add(dep.path)
        }
      }
    }
  }

  return (directDependencies.asSequence() + transitiveDepsForCompile).joinToString(File.pathSeparator)
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