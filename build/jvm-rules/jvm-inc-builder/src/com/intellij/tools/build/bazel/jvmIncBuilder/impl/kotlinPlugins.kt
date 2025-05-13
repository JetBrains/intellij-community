// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.impl

import androidx.compose.compiler.plugins.kotlin.ComposeCommandLineProcessor
import androidx.compose.compiler.plugins.kotlin.ComposePluginRegistrar
import org.jetbrains.kotlin.cli.jvm.plugins.PluginCliParser.RegisteredPluginInfo
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.compiler.plugin.PluginProcessingException
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.util.ServiceLoaderLite
import org.jetbrains.kotlinx.serialization.compiler.extensions.SerializationComponentRegistrar
import org.jetbrains.kotlinx.serialization.compiler.extensions.SerializationPluginOptions
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.net.URLClassLoader
import java.nio.file.Path
import kotlin.collections.iterator
import kotlin.getValue


@OptIn(ExperimentalCompilerApi::class)
fun configurePlugins(
  pluginIdToPluginClasspath: Map<String, String>,
  workingDir: Path,
  consumer: (RegisteredPluginInfo) -> Unit,
) {
  for ((id, paths) in pluginIdToPluginClasspath) {
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

      else -> {
        consumer(CompilerPluginProvider.provide(id))
      }
    }
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

private class CompilerPluginProvider {
  companion object {
    private val expects by getConstructor("fleet.multiplatform.expects.ExpectsPluginRegistrar", null)
    private val rhizomeDb by getConstructor(
      registrar = "com.jetbrains.rhizomedb.plugin.RhizomedbComponentRegistrar",
      commandLineProcessor = "com.jetbrains.rhizomedb.plugin.RhizomedbCommandLineProcessor",
    )

    fun provide(id: String): RegisteredPluginInfo {
      return when (id) {
        "jetbrains.fleet.expects-compiler-plugin" -> createPluginInfo(expects)
        "org.jetbrains.fleet.rhizomedb-compiler-plugin" -> createPluginInfo(rhizomeDb)
        else -> throw IllegalArgumentException("plugin requires classpath: $id")
      }
    }

  }
}

@OptIn(ExperimentalCompilerApi::class)
private fun createPluginInfo(data: Pair<MethodHandle, MethodHandle?>): RegisteredPluginInfo {
  return RegisteredPluginInfo(
    componentRegistrar = null,
    compilerPluginRegistrar = data.first.invoke() as CompilerPluginRegistrar,
    commandLineProcessor = data.second?.invoke() as CommandLineProcessor?,
    pluginOptions = emptyList(),
  )
}

private fun getConstructor(registrar: String, commandLineProcessor: String?): Lazy<Pair<MethodHandle, MethodHandle?>> {
  return lazy {
    findConstructor(registrar) to commandLineProcessor?.let { findConstructor(it) }
  }
}

private fun findConstructor(name: String): MethodHandle {
  val aClass = KotlinCompilerRunner::class.java.classLoader.loadClass(name)
  return MethodHandles.lookup().findConstructor(aClass, MethodType.methodType(Void.TYPE))
}