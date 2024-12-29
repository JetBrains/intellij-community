// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm.kotlin

import com.google.devtools.build.lib.view.proto.Deps
import com.google.devtools.build.lib.view.proto.Deps.Dependencies
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.ensureActive
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.*
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
import java.lang.reflect.Field
import java.nio.file.Files
import java.nio.file.Path
import kotlin.coroutines.coroutineContext

internal suspend fun compileKotlinForJvm(
  args: ArgMap<KotlinBuilderFlags>,
  context: TraceHelper,
  sources: List<Path>,
  out: Writer,
  workingDir: Path,
  info: CompilationTaskInfo,
): Int {
  val kotlinArgs = K2JVMCompilerArguments()
  kotlinArgs.noStdlib = true

  kotlinArgs.classpath = createClasspath(args, workingDir)

  kotlinArgs.apiVersion = args.optionalSingle(KotlinBuilderFlags.API_VERSION)
  kotlinArgs.languageVersion = args.optionalSingle(KotlinBuilderFlags.LANGUAGE_VERSION)
  // jdkRelease leads to some strange compilation failures like "cannot access class 'Map.Entry'"
  kotlinArgs.jvmTarget = args.mandatorySingle(KotlinBuilderFlags.JVM_TARGET).let { if (it == "8") "1.8" else it }
  kotlinArgs.moduleName = info.moduleName
  kotlinArgs.disableStandardScript = true
  // kotlin bug - not compatible with a new X compiler-plugin syntax
  //compilationArgs.disableDefaultScriptingPlugin = true
  args.optional(KotlinBuilderFlags.FRIEND_PATHS)?.let { value ->
    kotlinArgs.friendPaths = value.map { workingDir.resolve(it).toString() }.toTypedArray()
  }

  kotlinArgs.destination = workingDir.resolve(args.mandatorySingle(KotlinBuilderFlags.OUTPUT)).toString()

  args.optional(KotlinBuilderFlags.OPT_IN)?.let {
    kotlinArgs.optIn = it.toTypedArray()
  }
  val allowKotlinPackage = args.boolFlag(KotlinBuilderFlags.ALLOW_KOTLIN_PACKAGE)
  if (allowKotlinPackage) {
    kotlinArgs.allowKotlinPackage = true
  }

  args.optionalSingle(KotlinBuilderFlags.LAMBDAS)?.let {
    kotlinArgs.lambdas = it
  }
  args.optionalSingle(KotlinBuilderFlags.JVM_DEFAULT)?.let {
    kotlinArgs.jvmDefault = it
  }
  if (args.boolFlag(KotlinBuilderFlags.INLINE_CLASSES)) {
    kotlinArgs.inlineClasses = true
  }
  if (args.boolFlag(KotlinBuilderFlags.CONTEXT_RECEIVERS)) {
    kotlinArgs.contextReceivers = true
  }
  args.optionalSingle(KotlinBuilderFlags.WARN)?.let {
    when (it) {
      "off" -> kotlinArgs.suppressWarnings = true
      "error" -> kotlinArgs.allWarningsAsErrors = true
      else -> throw IllegalArgumentException("unsupported kotlinc warning option: $it")
    }
  }

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
  if (allowKotlinPackage) {
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
  if (code != 0 || context.isTracing) {
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
  }
  return code
}

private inline fun wrapOutput(out: Writer, label: String, classifier: String, task: () -> Unit) {
  out.appendLine("\u001B[1m=============== $classifier ($label) ===============\u001B[0m")
  task()
  out.appendLine("\u001B[1m=============== END of $classifier ($label) ===============\u001B[0m")
}

private fun getAllFields(): Sequence<Field> {
  return sequence {
    var aClass: Class<*>? = K2JVMCompilerArguments::class.java
    while (aClass != null) {
      yieldAll(aClass.declaredFields.asSequence())
      aClass = aClass.superclass.takeIf { it != Any::class.java }
    }
  }
}

// we do not have kotlin reflection
private fun <T : CommonToolArguments> toArgumentStrings(
  thisArgs: T,
  shortArgumentKeys: Boolean = false,
  compactArgumentValues: Boolean = true,
): List<String> {
  val result = ArrayList<String>()
  val defaultArguments = K2JVMCompilerArguments()
  for (field in getAllFields()) {
    val argAnnotation = field.getAnnotation(Argument::class.java) ?: continue
    field.setAccessible(true)
    val rawPropertyValue = field.get(thisArgs)
    val rawDefaultValue = field.get(defaultArguments)

    /* Default value can be omitted */
    if (rawPropertyValue == rawDefaultValue) {
      continue
    }

    val argumentStringValues = when {
      field.type == java.lang.Boolean.TYPE -> {
        listOf(rawPropertyValue?.toString() ?: false.toString())
      }
      field.type.isArray -> {
        getArgumentStringValue(
          argAnnotation = argAnnotation,
          values = rawPropertyValue as Array<*>?,
          compactArgValues = compactArgumentValues,
        )
      }
      field.type == java.util.List::class.java -> {
        getArgumentStringValue(
          argAnnotation = argAnnotation,
          values = (rawPropertyValue as List<*>?)?.toTypedArray(),
          compactArgValues = compactArgumentValues,
        )
      }
      else -> listOf(rawPropertyValue.toString())
    }

    val argumentName = if (shortArgumentKeys && argAnnotation.shortName.isNotEmpty()) argAnnotation.shortName else argAnnotation.value

    for (argumentStringValue in argumentStringValues) {
      when {
        /* We can just enable the flag by passing the argument name like -myFlag: Value not required */
        rawPropertyValue is Boolean && rawPropertyValue -> {
          result.add(argumentName)
        }

        /* Advanced (e.g. -X arguments) or boolean properties need to be passed using the '=' */
        argAnnotation.isAdvanced || field.type == java.lang.Boolean.TYPE -> {
          result.add("$argumentName=$argumentStringValue")
        }

        else -> {
          result.add(argumentName)
          result.add(argumentStringValue)
        }
      }
    }
  }

  result.addAll(thisArgs.freeArgs)
  result.addAll(thisArgs.internalArguments.map { it.stringRepresentation })
  return result
}

private fun getArgumentStringValue(argAnnotation: Argument, values: Array<*>?, compactArgValues: Boolean): List<String> {
  if (values.isNullOrEmpty()) {
    return emptyList()
  }

  val delimiter = argAnnotation.resolvedDelimiter
  return if (delimiter.isNullOrEmpty() || !compactArgValues) values.map { it.toString() } else listOf(values.joinToString(delimiter))
}

private fun createClasspath(args: ArgMap<KotlinBuilderFlags>, baseDir: Path): String {
  if (!args.boolFlag(KotlinBuilderFlags.REDUCED_CLASSPATH_MODE)) {
    return args.mandatory(KotlinBuilderFlags.CLASSPATH).joinToString(File.pathSeparator) { baseDir.resolve(it).normalize().toString() }
  }

  val directDependencies = args.mandatory(KotlinBuilderFlags.DIRECT_DEPENDENCIES)

  val depsArtifacts = args.optional(KotlinBuilderFlags.DEPS_ARTIFACTS) ?: return directDependencies.joinToString(File.pathSeparator)
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