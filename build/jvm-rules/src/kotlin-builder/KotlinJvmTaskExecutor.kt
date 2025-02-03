// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm.kotlin

import com.google.devtools.build.lib.view.proto.Deps
import com.google.devtools.build.lib.view.proto.Deps.Dependencies
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiJavaModule.MODULE_INFO_FILE
import kotlinx.coroutines.ensureActive
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.config.addKotlinSourceRoot
import org.jetbrains.kotlin.cli.common.messages.*
import org.jetbrains.kotlin.cli.common.moduleChunk
import org.jetbrains.kotlin.cli.common.modules.ModuleBuilder
import org.jetbrains.kotlin.cli.common.modules.ModuleChunk
import org.jetbrains.kotlin.cli.common.setupCommonArguments
import org.jetbrains.kotlin.cli.jvm.compiler.BazelJvmCliPipeline
import org.jetbrains.kotlin.cli.jvm.compiler.BazelJvmConfigurationPipelinePhase
import org.jetbrains.kotlin.cli.jvm.compiler.configTemplate
import org.jetbrains.kotlin.cli.jvm.compiler.configurePlugins
import org.jetbrains.kotlin.cli.jvm.compiler.loadPlugins
import org.jetbrains.kotlin.cli.jvm.config.JvmClasspathRoot
import org.jetbrains.kotlin.cli.jvm.config.JvmModulePathRoot
import org.jetbrains.kotlin.cli.jvm.config.addJavaSourceRoot
import org.jetbrains.kotlin.cli.jvm.setupJvmSpecificArguments
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.Services
import org.jetbrains.kotlin.config.messageCollector
import org.jetbrains.kotlin.config.moduleName
import org.jetbrains.kotlin.config.modules
import org.jetbrains.kotlin.metadata.deserialization.MetadataVersion
import org.jetbrains.kotlin.modules.JavaRootPath
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

  val pluginConfigurations = configurePlugins(args = args, workingDir = baseDir, label = info.label)

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

  val config = configTemplate.copy()
  config.put(CLIConfigurationKeys.ORIGINAL_MESSAGE_COLLECTOR_KEY, messageCollector)
  val collector = GroupingMessageCollector(messageCollector, kotlinArgs.allWarningsAsErrors, kotlinArgs.reportAllWarnings).also {
    config.messageCollector = it
  }
  config.setupCommonArguments(kotlinArgs) { version -> MetadataVersion(*version) }
  config.setupJvmSpecificArguments(kotlinArgs)
  if (args.boolFlag(JvmBuilderFlags.ALLOW_KOTLIN_PACKAGE)) {
    config.put(CLIConfigurationKeys.ALLOW_KOTLIN_PACKAGE, true)
  }

  config.put(JVMConfigurationKeys.OUTPUT_JAR, outFile.toFile())

  coroutineContext.ensureActive()

  val rootDisposable = Disposer.newDisposable("Disposable for Bazel Kotlin Compiler")
  var code = try {
    loadPlugins(configuration = config, pluginConfigurations = pluginConfigurations)

    val moduleName = info.moduleName
    config.moduleName = moduleName

    val module = ModuleBuilder(moduleName, outFilePath, "java-production")

    args.optional(JvmBuilderFlags.FRIENDS)?.let { value ->
      for (path in value) {
        module.addFriendDir(baseDir.resolve(path).normalize().toString())
      }
    }

    var isJava9Module =  false

    val moduleInfoNameSuffix = File.separatorChar + MODULE_INFO_FILE
    for (source in sources) {
      val path = source.toString()
      if (path.endsWith(".java")) {
        module.addJavaSourceRoot(JavaRootPath(path, null))
        config.addJavaSourceRoot(source.toFile(), null)
        if (!isJava9Module) {
          isJava9Module = path.endsWith(moduleInfoNameSuffix)
        }
      }
      else {
        module.addSourceFiles(path)
        config.addKotlinSourceRoot(path = path, isCommon = false, hmppModuleName = null)
      }
    }

    for (path in classPath) {
      module.addClasspathEntry(path.toString())
    }

    for (file in classPath) {
      val ioFile = file.toFile()
      if (isJava9Module) {
        config.add(CLIConfigurationKeys.CONTENT_ROOTS, JvmModulePathRoot(ioFile))
      }
      config.add(CLIConfigurationKeys.CONTENT_ROOTS, JvmClasspathRoot(ioFile))
    }

    val modules = listOf(module)
    config.modules = modules
    config.moduleChunk = ModuleChunk(modules)

    val pipeline = BazelJvmCliPipeline(BazelJvmConfigurationPipelinePhase(config))
    //val groupingMessageCollector = GroupingMessageCollector(messageCollector, false, false)
    //val argumentsInput = ArgumentsPipelineArtifact(
    //  kotlinArgs,
    //  Services.EMPTY,
    //  rootDisposable,
    //  groupingMessageCollector,
    //  pipeline.defaultPerformanceManager,
    //)

    pipeline.execute(kotlinArgs, Services.EMPTY, messageCollector).code
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
    return args.mandatory(JvmBuilderFlags.CLASSPATH).map { baseDir.resolve(it).normalize() }
  }

  val directDependencies = args.mandatory(JvmBuilderFlags.DIRECT_DEPENDENCIES)

  val depsArtifacts = args.optional(JvmBuilderFlags.DEPS_ARTIFACTS) ?: return directDependencies.map { baseDir.resolve(it).normalize() }
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