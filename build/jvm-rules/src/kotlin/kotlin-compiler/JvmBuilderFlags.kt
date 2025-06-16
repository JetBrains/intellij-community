// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm.kotlin

import com.dynatrace.hash4j.hashing.HashStream64
import org.jetbrains.bazel.jvm.util.ArgMap
import org.jetbrains.bazel.jvm.util.createArgMap
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import java.nio.file.Files
import java.nio.file.Path
import java.util.regex.Pattern

private val FLAG_FILE_RE: Regex = Pattern.compile("""^--flagfile=((.*)-(\d+).params)$""").toRegex()

fun parseArgs(args: Array<String>): ArgMap<JvmBuilderFlags> {
  check(args.isNotEmpty()) {
    "expected at least a single arg got: ${args.joinToString(" ")}"
  }

  return createArgMap(
    args = FLAG_FILE_RE.matchEntire(args[0])?.groups?.get(1)?.let {
      Files.readAllLines(Path.of(it.value))
    } ?: args.asList(),
    enumClass = JvmBuilderFlags::class.java,
  )
}

enum class JvmBuilderFlags {
  NON_INCREMENTAL,
  JAVA_COUNT,
  TARGET_LABEL,
  // classpath
  CP,

  PLUGIN_ID,
  PLUGIN_CLASSPATH,

  OUT,
  ABI_OUT,

  KOTLIN_MODULE_NAME,

  API_VERSION,
  LANGUAGE_VERSION,
  JVM_TARGET,

  OPT_IN,
  ALLOW_KOTLIN_PACKAGE,
  WHEN_GUARDS,
  LAMBDAS,
  JVM_DEFAULT,
  INLINE_CLASSES,
  CONTEXT_RECEIVERS,

  WARN,

  FRIENDS,

  // makes sense only for JPS, not needed for Kotlinc
  ADD_EXPORT,
}

fun configureCommonCompilerArgs(kotlinArgs: K2JVMCompilerArguments, args: ArgMap<JvmBuilderFlags>, workingDir: Path, configHash: HashStream64) {
  // @todo check for  -Xskip-prerelease-check -Xallow-unstable-dependencies during import

  // Not used, but can be used later
  configHash.putStringOrNull(kotlinArgs.jdkHome)
  configHash.putStringOrNull(kotlinArgs.jdkRelease)

  kotlinArgs.skipPrereleaseCheck = true
  configHash.putBoolean(kotlinArgs.skipPrereleaseCheck)

  kotlinArgs.allowUnstableDependencies = true
  configHash.putBoolean(kotlinArgs.allowUnstableDependencies)

  kotlinArgs.noStdlib = true
  configHash.putBoolean(kotlinArgs.noStdlib)

  kotlinArgs.noReflect = true
  configHash.putBoolean(kotlinArgs.noReflect)

  kotlinArgs.disableStandardScript = true
  configHash.putBoolean(kotlinArgs.disableStandardScript)

  kotlinArgs.apiVersion = args.optionalSingle(JvmBuilderFlags.API_VERSION)
  configHash.putStringOrNull(kotlinArgs.apiVersion)

  kotlinArgs.languageVersion = args.optionalSingle(JvmBuilderFlags.LANGUAGE_VERSION)
  configHash.putStringOrNull(kotlinArgs.languageVersion)

  // jdkRelease leads to some strange compilation failures like "cannot access class 'Map.Entry'"
  kotlinArgs.jvmTarget = getJvmTargetLevel(args)
  configHash.putStringOrNull(kotlinArgs.jvmTarget)

  // kotlin bug - not compatible with a new X compiler-plugin syntax
  //compilationArgs.disableDefaultScriptingPlugin = true

  // TODO not sure what to do with friendPaths. They should be configHash somehow as well
  //  but in canonical form to be equal between different roots and OSes
  args.optional(JvmBuilderFlags.FRIENDS)?.let { value ->
    kotlinArgs.friendPaths = value.map { workingDir.resolve(it).normalize().toString() }.toTypedArray()
  }

  args.optional(JvmBuilderFlags.OPT_IN)?.let {
    kotlinArgs.optIn = it.toTypedArray()
  }
  configHash.putStringListOrNull(kotlinArgs.optIn)

  if (args.boolFlag(JvmBuilderFlags.ALLOW_KOTLIN_PACKAGE)) {
    kotlinArgs.allowKotlinPackage = true
  }
  configHash.putBoolean(kotlinArgs.allowKotlinPackage)

  if (args.boolFlag(JvmBuilderFlags.WHEN_GUARDS)) {
    kotlinArgs.whenGuards = true
  }
  configHash.putBoolean(kotlinArgs.whenGuards)

  args.optionalSingle(JvmBuilderFlags.LAMBDAS)?.let {
    kotlinArgs.lambdas = it
  }
  configHash.putStringOrNull(kotlinArgs.lambdas)

  args.optionalSingle(JvmBuilderFlags.JVM_DEFAULT)?.let {
    kotlinArgs.jvmDefault = it
  }
  configHash.putString(kotlinArgs.jvmDefault ?: "")

  if (args.boolFlag(JvmBuilderFlags.INLINE_CLASSES)) {
    kotlinArgs.inlineClasses = true
  }
  configHash.putBoolean(kotlinArgs.inlineClasses)

  if (args.boolFlag(JvmBuilderFlags.CONTEXT_RECEIVERS)) {
    kotlinArgs.contextReceivers = true
  }
  configHash.putBoolean(kotlinArgs.contextReceivers)

  args.optionalSingle(JvmBuilderFlags.WARN)?.let {
    when (it) {
      "off" -> kotlinArgs.suppressWarnings = true
      "error" -> kotlinArgs.allWarningsAsErrors = true
      else -> throw IllegalArgumentException("unsupported kotlinc warning option: $it")
    }
  }
  configHash.putBoolean(kotlinArgs.suppressWarnings)
  configHash.putBoolean(kotlinArgs.allWarningsAsErrors)
}

fun HashStream64.putStringOrNull(s: String?) {
  putBoolean(s != null)
  s?.let { putString(it) }
}

fun HashStream64.putStringListOrNull(list: List<String>?) {
  putBoolean(list != null)
  list?.let {
    putInt(it.size)
    it.forEach { putString(it) }
  }
}

fun HashStream64.putStringListOrNull(list: Array<String>?) {
  putStringListOrNull(list?.asList())
}

fun getJvmTargetLevel(args: ArgMap<JvmBuilderFlags>): String {
  return args.optionalSingle(JvmBuilderFlags.JVM_TARGET)?.let { if (it == "8") "1.8" else it } ?: "21"
}