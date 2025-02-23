// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm.kotlin

import androidx.compose.compiler.plugins.kotlin.ComposeCommandLineProcessor
import androidx.compose.compiler.plugins.kotlin.ComposePluginRegistrar
import io.bazel.kotlin.plugin.jdeps.JdepsGenCommandLineProcessor
import io.bazel.kotlin.plugin.jdeps.JdepsGenComponentRegistrar
import org.jetbrains.bazel.jvm.ArgMap
import org.jetbrains.kotlin.cli.jvm.plugins.PluginCliParser.RegisteredPluginInfo
import org.jetbrains.kotlin.compiler.plugin.CliOptionValue
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.compiler.plugin.PluginProcessingException
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.jvm.abi.JvmAbiCommandLineProcessor
import org.jetbrains.kotlin.jvm.abi.JvmAbiComponentRegistrar
import org.jetbrains.kotlin.util.ServiceLoaderLite
import org.jetbrains.kotlinx.serialization.compiler.extensions.SerializationComponentRegistrar
import org.jetbrains.kotlinx.serialization.compiler.extensions.SerializationPluginOptions
import java.net.URLClassLoader
import java.nio.file.Path

private fun cliOptionValue(name: String, value: String) = CliOptionValue(pluginId = "<NO_ID>", optionName = name, value = value)

@OptIn(ExperimentalCompilerApi::class)
internal inline fun configurePlugins(
  args: ArgMap<JvmBuilderFlags>,
  workingDir: Path,
  targetLabel: String,
  consumer: (RegisteredPluginInfo) -> Unit,
) {
  // put user plugins first
  val plugins = args.optionalList(JvmBuilderFlags.PLUGIN_ID).zip(args.optionalList(JvmBuilderFlags.PLUGIN_CLASSPATH))
  for ((id, paths) in plugins) {
    val classpath = if (paths.isBlank()) {
      emptyList()
    }
    else {
      paths.splitToSequence(':').map { workingDir.resolve(it).toAbsolutePath().normalize() }.toList()
    }
    if (classpath.isNotEmpty()) {
      consumer(loadRegisteredPluginsInfo(classpath))
      continue
    }

    @Suppress("SpellCheckingInspection")
    when (id) {
      "org.jetbrains.kotlin.kotlin-serialization-compiler-plugin" -> {
        consumer(RegisteredPluginInfo(
          componentRegistrar = null,
          compilerPluginRegistrar = SerializationComponentRegistrar(),
          commandLineProcessor = SerializationPluginOptions(),
          pluginOptions = emptyList(),
        ))
      }

      "org.jetbrains.kotlin.kotlin-compose-compiler-plugin" -> {
        consumer(RegisteredPluginInfo(
          componentRegistrar = null,
          compilerPluginRegistrar = ComposePluginRegistrar(),
          commandLineProcessor = ComposeCommandLineProcessor(),
          pluginOptions = emptyList(),
        ))
      }

      else -> throw IllegalArgumentException("plugin requires classpath: $id")
    }
  }

  args.optionalSingle(JvmBuilderFlags.JDEPS_OUT)?.let { workingDir.resolve(it) }?.let { jdeps ->
    val options = mutableListOf(
      cliOptionValue("output", jdeps.toString()),
      cliOptionValue("target_label", targetLabel),
    )
    args.optionalSingle(JvmBuilderFlags.STRICT_KOTLIN_DEPS)?.let {
      options.add(cliOptionValue("strict_kotlin_deps", it))
    }
    consumer(RegisteredPluginInfo(
      componentRegistrar = null,
      compilerPluginRegistrar = JdepsGenComponentRegistrar(),
      commandLineProcessor = JdepsGenCommandLineProcessor(),
      pluginOptions = options,
    ))
  }

  args.optionalSingle(JvmBuilderFlags.ABI_OUT)?.let { workingDir.resolve(it) }?.let { abiJar ->
    consumer(RegisteredPluginInfo(
      componentRegistrar = null,
      compilerPluginRegistrar = JvmAbiComponentRegistrar(),
      commandLineProcessor = JvmAbiCommandLineProcessor(),
      pluginOptions = listOf(
        cliOptionValue("outputDir", abiJar.toString()),
        cliOptionValue("targetLabel", targetLabel),
        cliOptionValue("removePrivateClasses", "true"),
      )))
  }
}

@OptIn(ExperimentalCompilerApi::class)
@Suppress("DEPRECATION")
private fun loadRegisteredPluginsInfo(classpath: List<Path>): RegisteredPluginInfo {
  val classLoader = URLClassLoader(
    classpath.map { it.toUri().toURL() }.toTypedArray(),
    CompilerConfiguration::class.java.classLoader
  )

  val files = classpath.map { it.toFile() }
  val compilerPluginRegistrars = ServiceLoaderLite.loadImplementations(CompilerPluginRegistrar::class.java, files, classLoader)
  fun multiplePluginsErrorMessage(pluginObjects: List<Any>): String {
    return buildString {
      append("Multiple plugins found in given classpath: ")
      appendLine(pluginObjects.mapNotNull { it::class.qualifiedName }.joinToString(", "))
      append("  Plugin configuration is: $classpath")
    }
  }

  when (compilerPluginRegistrars.size) {
    0 -> throw PluginProcessingException("No plugins found in given classpath: $classpath")
    1 -> {}
    else -> throw PluginProcessingException(multiplePluginsErrorMessage(compilerPluginRegistrars))
  }

  val commandLineProcessor = ServiceLoaderLite.loadImplementations(CommandLineProcessor::class.java, files, classLoader)
  if (commandLineProcessor.size > 1) {
    throw PluginProcessingException(multiplePluginsErrorMessage(commandLineProcessor))
  }

  return RegisteredPluginInfo(
    componentRegistrar = null,
    compilerPluginRegistrar = compilerPluginRegistrars.firstOrNull(),
    commandLineProcessor = commandLineProcessor.firstOrNull(),
    pluginOptions = emptyList(),
  )
}