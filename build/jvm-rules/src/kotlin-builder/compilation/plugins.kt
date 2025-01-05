// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.cli.jvm.compiler

import androidx.compose.compiler.plugins.kotlin.ComposeCommandLineProcessor
import androidx.compose.compiler.plugins.kotlin.ComposePluginRegistrar
import dev.drewhamilton.poko.PokoCommandLineProcessor
import dev.drewhamilton.poko.PokoCompilerPluginRegistrar
import io.bazel.kotlin.plugin.jdeps.JdepsGenCommandLineProcessor
import io.bazel.kotlin.plugin.jdeps.JdepsGenComponentRegistrar
import org.jetbrains.bazel.jvm.kotlin.ArgMap
import org.jetbrains.bazel.jvm.kotlin.JvmBuilderFlags
import org.jetbrains.kotlin.cli.jvm.plugins.PluginCliParser.RegisteredPluginInfo
import org.jetbrains.kotlin.compiler.plugin.CliOptionValue
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.jvm.abi.JvmAbiCommandLineProcessor
import org.jetbrains.kotlin.jvm.abi.JvmAbiComponentRegistrar
import org.jetbrains.kotlinx.serialization.compiler.extensions.SerializationComponentRegistrar
import org.jetbrains.kotlinx.serialization.compiler.extensions.SerializationPluginOptions
import java.nio.file.Path

internal data class CompilerPluginDescriptor(
  @JvmField val info: RegisteredPluginInfo?,
  @JvmField val classpath: List<String>,
  @JvmField val options: List<CliOptionValue>,
)

private fun cliOptionValue(name: String, value: String) = CliOptionValue(pluginId = "<NO_ID>", optionName = name, value = value)

@OptIn(ExperimentalCompilerApi::class)
internal fun configurePlugins(
  args: ArgMap<JvmBuilderFlags>,
  workingDir: Path,
  label: String,
): List<CompilerPluginDescriptor> {
  val pluginConfigurations = mutableListOf<CompilerPluginDescriptor>()

  fun addPlugin(info: RegisteredPluginInfo) {
    pluginConfigurations.add(CompilerPluginDescriptor(info = info, classpath = emptyList(), options = emptyList()))
  }

  // put user plugins first
  val plugins = args.optionalList(JvmBuilderFlags.PLUGIN_ID).zip(args.optionalList(JvmBuilderFlags.PLUGIN_CLASSPATH))
  for ((id, paths) in plugins) {
    val classpath = if (paths.isBlank()) {
      emptyList()
    }
    else {
      paths.splitToSequence(':').map { workingDir.resolve(it).toAbsolutePath().normalize().toString() }.toList()
    }
    if (classpath.isNotEmpty()) {
      pluginConfigurations.add(CompilerPluginDescriptor(info = null, classpath = classpath, options = emptyList()))
      continue
    }

    @Suppress("SpellCheckingInspection")
    when (id) {
      "org.jetbrains.kotlin.kotlin-serialization-compiler-plugin" -> {
        addPlugin(RegisteredPluginInfo(
          componentRegistrar = null,
          compilerPluginRegistrar = SerializationComponentRegistrar(),
          commandLineProcessor = SerializationPluginOptions(),
          pluginOptions = emptyList(),
        ))
      }

      "org.jetbrains.kotlin.kotlin-compose-compiler-plugin" -> {
        addPlugin(RegisteredPluginInfo(
          componentRegistrar = null,
          compilerPluginRegistrar = ComposePluginRegistrar(),
          commandLineProcessor = ComposeCommandLineProcessor(),
          pluginOptions = emptyList(),
        ))
      }

      "dev.drewhamilton.poko.poko-compiler-plugin" -> {
        addPlugin(RegisteredPluginInfo(
          componentRegistrar = null,
          compilerPluginRegistrar = PokoCompilerPluginRegistrar(),
          commandLineProcessor = PokoCommandLineProcessor(),
          pluginOptions = emptyList(),
        ))
      }

      else -> throw IllegalArgumentException("plugin requires classpath: $id")
    }
  }

  args.optionalSingle(JvmBuilderFlags.JDEPS_OUT)?.let { workingDir.resolve(it) }?.let { jdeps ->
    val options = mutableListOf(
      cliOptionValue("output", jdeps.toString()),
      cliOptionValue("target_label", label),
    )
    args.optionalSingle(JvmBuilderFlags.STRICT_KOTLIN_DEPS)?.let {
      options.add(cliOptionValue("strict_kotlin_deps", it))
    }
    addPlugin(RegisteredPluginInfo(
      componentRegistrar = null,
      compilerPluginRegistrar = JdepsGenComponentRegistrar(),
      commandLineProcessor = JdepsGenCommandLineProcessor(),
      pluginOptions = options,
    ))
  }

  args.optionalSingle(JvmBuilderFlags.ABI_OUT)?.let { workingDir.resolve(it) }?.let { abiJar ->
    addPlugin(RegisteredPluginInfo(
      componentRegistrar = null,
      compilerPluginRegistrar = JvmAbiComponentRegistrar(),
      commandLineProcessor = JvmAbiCommandLineProcessor(),
      pluginOptions = listOf(
        cliOptionValue("outputDir", abiJar.toString()),
        cliOptionValue("targetLabel", label),
        cliOptionValue("removePrivateClasses", "true"),
      )))
  }

  return pluginConfigurations
}
