// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm.kotlin

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
  TARGET_LABEL,
  CLASSPATH,
  DIRECT_DEPENDENCIES,
  DEPS_ARTIFACTS,

  PLUGIN_ID,
  PLUGIN_CLASSPATH,

  OUT,
  ABI_OUT,

  RULE_KIND,
  KOTLIN_MODULE_NAME,

  API_VERSION,
  LANGUAGE_VERSION,
  JVM_TARGET,

  OPT_IN,
  ALLOW_KOTLIN_PACKAGE,
  LAMBDAS,
  JVM_DEFAULT,
  INLINE_CLASSES,
  CONTEXT_RECEIVERS,

  WARN,

  FRIENDS,
  JDEPS_OUT,
  TRACE,
  STRICT_KOTLIN_DEPS,
  REDUCED_CLASSPATH_MODE,

  // makes sense only for JPS, not needed for Kotlinc
  ADD_EXPORT,
}

fun configureCommonCompilerArgs(kotlinArgs: K2JVMCompilerArguments, args: ArgMap<JvmBuilderFlags>, workingDir: Path) {
  // @todo check for  -Xskip-prerelease-check -Xallow-unstable-dependencies during import
  kotlinArgs.skipPrereleaseCheck = true
  kotlinArgs.allowUnstableDependencies = true

  kotlinArgs.noStdlib = true
  kotlinArgs.disableStandardScript = true

  kotlinArgs.apiVersion = args.optionalSingle(JvmBuilderFlags.API_VERSION)
  kotlinArgs.languageVersion = args.optionalSingle(JvmBuilderFlags.LANGUAGE_VERSION)
  // jdkRelease leads to some strange compilation failures like "cannot access class 'Map.Entry'"
  kotlinArgs.jvmTarget = args.mandatorySingle(JvmBuilderFlags.JVM_TARGET).let { if (it == "8") "1.8" else it }

  // kotlin bug - not compatible with a new X compiler-plugin syntax
  //compilationArgs.disableDefaultScriptingPlugin = true
  args.optional(JvmBuilderFlags.FRIENDS)?.let { value ->
    kotlinArgs.friendPaths = value.map { workingDir.resolve(it).normalize().toString() }.toTypedArray()
  }

  args.optional(JvmBuilderFlags.OPT_IN)?.let {
    kotlinArgs.optIn = it.toTypedArray()
  }

  val allowKotlinPackage = args.boolFlag(JvmBuilderFlags.ALLOW_KOTLIN_PACKAGE)
  if (allowKotlinPackage) {
    kotlinArgs.allowKotlinPackage = true
  }

  args.optionalSingle(JvmBuilderFlags.LAMBDAS)?.let {
    kotlinArgs.lambdas = it
  }
  args.optionalSingle(JvmBuilderFlags.JVM_DEFAULT)?.let {
    kotlinArgs.jvmDefault = it
  }
  if (args.boolFlag(JvmBuilderFlags.INLINE_CLASSES)) {
    kotlinArgs.inlineClasses = true
  }
  if (args.boolFlag(JvmBuilderFlags.CONTEXT_RECEIVERS)) {
    kotlinArgs.contextReceivers = true
  }
  args.optionalSingle(JvmBuilderFlags.WARN)?.let {
    when (it) {
      "off" -> kotlinArgs.suppressWarnings = true
      "error" -> kotlinArgs.allWarningsAsErrors = true
      else -> throw IllegalArgumentException("unsupported kotlinc warning option: $it")
    }
  }
}