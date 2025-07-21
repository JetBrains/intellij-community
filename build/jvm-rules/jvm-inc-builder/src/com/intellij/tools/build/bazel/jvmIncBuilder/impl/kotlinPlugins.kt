// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.impl

import androidx.compose.compiler.plugins.kotlin.ComposeCommandLineProcessor
import androidx.compose.compiler.plugins.kotlin.ComposePluginRegistrar
import com.intellij.tools.build.bazel.jvmIncBuilder.StorageManager
import com.intellij.tools.build.bazel.jvmIncBuilder.runner.OutputOrigin
import com.intellij.tools.build.bazel.jvmIncBuilder.runner.OutputSink
import com.jetbrains.rhizomedb.plugin.RhizomedbCommandLineProcessor
import com.jetbrains.rhizomedb.plugin.RhizomedbComponentRegistrar
import org.jetbrains.kotlin.backend.common.output.OutputFileCollection
import org.jetbrains.kotlin.cli.jvm.plugins.PluginCliParser.RegisteredPluginInfo
import org.jetbrains.kotlin.compiler.plugin.*
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.jvm.abi.JvmAbiCommandLineProcessor
import org.jetbrains.kotlin.jvm.abi.JvmAbiComponentRegistrar
import org.jetbrains.kotlin.util.ServiceLoaderLite
import org.jetbrains.kotlinx.serialization.compiler.extensions.SerializationComponentRegistrar
import org.jetbrains.kotlinx.serialization.compiler.extensions.SerializationPluginOptions
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.net.URLClassLoader
import java.nio.file.Path


@OptIn(ExperimentalCompilerApi::class)
fun configurePlugins(
  pluginIdToPluginClasspath: Map<String, String>,
  workingDir: Path,
  abiConsumer: ((OutputFileCollection) -> Unit)?,
  out: OutputSink,
  storageManager: StorageManager,
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

      "org.jetbrains.fleet.rhizomedb-compiler-plugin" -> {
        val fileProvider = RhizomedbFileProvider(out, storageManager)
        consumer(RegisteredPluginInfo(
          componentRegistrar = null,
          compilerPluginRegistrar = RhizomedbComponentRegistrar(fileProvider.createReadProvider(), fileProvider.createWriteProvider()),
          commandLineProcessor = RhizomedbCommandLineProcessor(),
                 pluginOptions = emptyList(),
        ))
      }

      else -> {
        consumer(CompilerPluginProvider.provide(id))
      }
    }
  }

  if (abiConsumer != null) {
    val jvmAbiCommandLineProcessor = JvmAbiCommandLineProcessor()
    val pluginId = jvmAbiCommandLineProcessor.pluginId
    consumer(RegisteredPluginInfo(
      componentRegistrar = null,
      compilerPluginRegistrar = JvmAbiComponentRegistrar(abiConsumer),
      commandLineProcessor = jvmAbiCommandLineProcessor,
      pluginOptions = listOf(
        CliOptionValue(pluginId, JvmAbiCommandLineProcessor.OUTPUT_PATH_OPTION.optionName, ""), // Placeholder to satisfy the "required option" condition. The output is collected into memory
        CliOptionValue(pluginId, JvmAbiCommandLineProcessor.REMOVE_DATA_CLASS_COPY_IF_CONSTRUCTOR_IS_PRIVATE_OPTION.optionName, "true"),
        CliOptionValue(pluginId, JvmAbiCommandLineProcessor.REMOVE_PRIVATE_CLASSES_OPTION.optionName, "true"),
        CliOptionValue(pluginId, JvmAbiCommandLineProcessor.REMOVE_DEBUG_INFO_OPTION.optionName, "false"), // retain debug info, so that debug info in targets that depend on this one can be properly generated
      )
    ))
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
    private val rpc by getConstructor(
      registrar = "com.jetbrains.fleet.rpc.plugin.RpcComponentRegistrar",
      commandLineProcessor = "com.jetbrains.fleet.rpc.plugin.RpcCommandLineProcessor",
    )
    private val noria by getConstructor(
      registrar = "noria.plugin.NoriaComponentRegistrar",
      commandLineProcessor = "noria.plugin.NoriaCommandLineProcessor",
    )

    fun provide(id: String): RegisteredPluginInfo {
      return when (id) {
        "jetbrains.fleet.expects-compiler-plugin" -> createPluginInfo(expects)
        "com.jetbrains.fleet.rpc-compiler-plugin" -> createPluginInfo(rpc)
        "jetbrains.fleet.noria-compiler-plugin" -> createPluginInfo(noria)
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

class RhizomedbFileProvider(private val out: OutputSink, private val storageManager: StorageManager) {
  fun readFile(filePath: String): List<String> {
    val outputBuilder: ZipOutputBuilderImpl = storageManager.getOutputBuilder()
    val moduleEntryPath: String = outputBuilder.listEntries("META-INF/").singleOrNull { n -> n.endsWith(filePath) } ?: return emptyList()
    return outputBuilder.getContent(moduleEntryPath)?.toString(Charsets.UTF_8)?.split("\n") ?: emptyList()
  }

  fun writeFile(filePath: String, lines: Collection<String>) {
    lines.joinToString(separator = "\n").toByteArray(Charsets.UTF_8)
    out.addFile(
      OutputFileImpl(filePath, com.intellij.tools.build.bazel.jvmIncBuilder.runner.OutputFile.Kind.other, lines.joinToString(separator = "\n").toByteArray(Charsets.UTF_8), false),
      OutputOrigin.create(OutputOrigin.Kind.kotlin, emptyList())
    )
  }

  fun createReadProvider(): (String) -> List<String> = { filePath -> readFile(filePath) }
  fun createWriteProvider(): (String, Collection<String>) -> Unit = { filePath, lines -> writeFile(filePath, lines) }
}
