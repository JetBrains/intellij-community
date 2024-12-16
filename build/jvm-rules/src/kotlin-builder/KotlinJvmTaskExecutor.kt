/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.jetbrains.bazel.jvm.kotlin

import com.google.devtools.build.lib.view.proto.Deps
import com.google.devtools.build.lib.view.proto.Deps.Dependencies
import com.google.devtools.build.runfiles.Runfiles
import org.jetbrains.kotlin.buildtools.api.*
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.*
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.compilerRunner.toArgumentStrings
import org.jetbrains.kotlin.config.Services
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.jvm.java

private val runFiles by lazy {
  Runfiles.preload().unmapped()
}

private fun resolveVerifiedFromProperty(key: String): String {
  val path = (System.getProperty(key) ?: throw FileNotFoundException("no reference for $key in ${System.getProperties()}"))
  return path.splitToSequence(',').map { runFiles.rlocation(it) }.joinToString(",")
}

internal fun doCompileKotlin(
  task: JvmCompilationTask,
  context: CompilationTaskContext,
  sources: List<File>,
): Int {
  val args = K2JVMCompilerArguments()
  args.noStdlib = true
  args.classpath = createClasspath(task)

  args.apiVersion = task.args.optionalSingle(KotlinBuilderFlags.API_VERSION)
  args.languageVersion = task.args.optionalSingle(KotlinBuilderFlags.LANGUAGE_VERSION)
  args.jvmTarget = task.args.mandatorySingle(KotlinBuilderFlags.JVM_TARGET)
  args.moduleName = task.info.moduleName
  args.disableStandardScript = true
  // kotlin bug - not compatible with a new X compiler-plugin syntax
  //compilationArgs.disableDefaultScriptingPlugin = true
  task.args.optional(KotlinBuilderFlags.KOTLIN_FRIEND_PATHS)?.let { value ->
    args.friendPaths = value.map { task.workingDir.resolve(it).toString() }.toTypedArray()
  }
  args.destination = task.outJar.toString()

  task.args.optional(KotlinBuilderFlags.OPT_IN)?.let {
    args.optIn = it.toTypedArray()
  }
  if (task.args.boolFlag(KotlinBuilderFlags.ALLOW_KOTLIN_PACKAGE)) {
    args.allowKotlinPackage = true
  }

  task.args.optionalSingle(KotlinBuilderFlags.LAMBDAS)?.let {
    args.lambdas = it
  }
  task.args.optionalSingle(KotlinBuilderFlags.JVM_DEFAULT)?.let {
    args.jvmDefault = it
  }
  if (task.args.boolFlag(KotlinBuilderFlags.INLINE_CLASSES)) {
    args.inlineClasses = true
  }
  if (task.args.boolFlag(KotlinBuilderFlags.CONTEXT_RECEIVERS)) {
    args.contextReceivers = true
  }
  task.args.optionalSingle(KotlinBuilderFlags.WARN)?.let {
    when (it) {
      "off" -> args.suppressWarnings = true
      "error" -> args.allWarningsAsErrors = true
      else -> throw IllegalArgumentException("unsupported kotlinc warning option: $it")
    }
  }

  configurePlugins(args = args, task = task)

  if (context.isTracing) {
    val label = task.info.label
    context.out.appendLine("\u001B[1m=============== K2JVM Compiler Arguments ($label) ===============\u001B[0m")
    context.out.appendLine(args.toArgumentStrings().joinToString("\n"))
    context.out.appendLine("\u001B[1m=============== END of K2JVM Compiler Arguments ($label) ===============\u001B[0m")
  }

  val compiler = K2JVMCompiler()
  require(args.freeArgs.isEmpty())
  args.freeArgs = sources.map { it.absolutePath }
  val logCollector = WriterBackedMessageCollector(verbose = context.isTracing)
  val result = compiler.exec(
    messageCollector = logCollector,
    services = Services.EMPTY,
    arguments = args,
  )
  if (result.code != 0 || context.isTracing) {
    for (entry in logCollector.entries) {
      context.out.appendLine(MessageRenderer.PLAIN_RELATIVE_PATHS.render(entry.severity, entry.message, entry.location))
    }
  }
  return result.code
}

//internal fun packOutput(
//  task: JvmCompilationTask,
//  context: CompilationTaskContext,
//) {
//  val outputs = task.outputs
//  if (outputs.jar != null) {
//    context.execute("create jar") {
//      val packageIndexBuilder = PackageIndexBuilder()
//      ZipArchiveOutputStream(
//        channel = FileChannel.open(task.outputs.jar, W_OVERWRITE),
//        zipIndexWriter = ZipIndexWriter(indexWriter = packageIndexBuilder.indexWriter),
//      ).use { out ->
//        JarCreator(
//          packageIndexBuilder = packageIndexBuilder,
//          targetLabel = task.info.label,
//          injectingRuleKind = "kt_${task.info.ruleKind.name.lowercase()}",
//          out = out,
//        ).use {
//          it.addDirectory(task.directories.classes)
//        }
//      }
//    }
//  }
//}

internal fun createClasspath(task: JvmCompilationTask): String {
  if (!task.args.optionalSingle(KotlinBuilderFlags.REDUCED_CLASSPATH_MODE).toBoolean()) {
    return task.inputs.classpath.joinToString(File.pathSeparator) { it.toString() }
  }

  val transitiveDepsForCompile = LinkedHashSet<String>()
  for (jdepsPath in task.inputs.depsArtifacts) {
    BufferedInputStream(Files.newInputStream(Path.of(jdepsPath))).use {
      val deps = Dependencies.parseFrom(it)
      for (dep in deps.dependencyList) {
        if (dep.kind == Deps.Dependency.Kind.EXPLICIT) {
          transitiveDepsForCompile.add(dep.path)
        }
      }
    }
  }

  return (task.inputs.directDependencies.asSequence() + transitiveDepsForCompile)
    .joinToString(File.pathSeparator)
}

private fun configurePlugins(
  task: JvmCompilationTask,
  args: K2JVMCompilerArguments,
) {
  val pluginConfigurations = mutableListOf<String>()

  // put user plugins first
  for (path in task.inputs.compilerPluginClasspath) {
    pluginConfigurations.add(path.toString())
  }

  val outputs = task.outputs
  outputs.jdeps?.let {
    var s = "${resolveVerifiedFromProperty("kotlin.bazel.jdeps.plugin")}=output=${outputs.jdeps},target_label=${task.info.label}"
    task.args.optionalSingle(KotlinBuilderFlags.STRICT_KOTLIN_DEPS)?.let {
      s += ",strict_kotlin_deps=$it"
    }
    pluginConfigurations.add(s)
  }
  outputs.abiJar?.let {
    pluginConfigurations.add("${resolveVerifiedFromProperty("kotlin.bazel.abi.plugin")}=outputDir=${outputs.abiJar},removePrivateClasses=true")
  }

  if (pluginConfigurations.isNotEmpty()) {
    args.pluginConfigurations = pluginConfigurations.toTypedArray()
  }
}

private val kotlinProjectId = ProjectId.ProjectUUID(UUID.randomUUID())

@Suppress("unused")
@OptIn(ExperimentalBuildToolsApi::class)
private fun incrementalCompilation(context: CompilationTaskContext, sources: List<File>, args: List<String>) {
  val service = CompilationService.loadImplementation(context::class.java.classLoader)
  val strategyConfig = service.makeCompilerExecutionStrategyConfiguration()
  val compilationConfig = service
    .makeJvmCompilationConfiguration()
    .useLogger(WorkerKotlinLogger(out = context.out, isDebugEnabled = context.isTracing))
  val result = service.compileJvm(
    projectId = kotlinProjectId,
    strategyConfig = strategyConfig,
    compilationConfig = compilationConfig,
    sources = sources,
    arguments = args,
  )
  if (result != CompilationResult.COMPILATION_SUCCESS) {
    throw CompilationStatusException("compile phase failed", result.ordinal)
  }
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

  @JvmField val entries = mutableListOf<LogMessage>()

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
    entries.add(LogMessage(severity = severity, message = message, location = location))
  }

  override fun hasErrors(): Boolean = hasErrors
}

private class WorkerKotlinLogger(private val out: Writer, override val isDebugEnabled: Boolean) : KotlinLogger {
  override fun error(msg: String, throwable: Throwable?) {
    out.appendLine(msg)
    throwable?.let {
      PrintWriter(out).use {
        throwable.printStackTrace(it)
      }
    }
  }

  override fun warn(msg: String) {
    out.append("WARN: ").appendLine(msg)
  }

  override fun info(msg: String) {
    out.append("INFO: ").appendLine(msg)
  }

  override fun debug(msg: String) {
    if (isDebugEnabled) {
      out.append("DEBUG: ").appendLine(msg)
    }
  }

  override fun lifecycle(msg: String) {
    out.appendLine(msg)
  }
}