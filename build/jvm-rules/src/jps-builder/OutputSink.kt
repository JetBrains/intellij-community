// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm.jps

import kotlinx.coroutines.channels.SendChannel
import org.jetbrains.bazel.jvm.abi.JarContentToProcess
import org.jetbrains.bazel.jvm.jps.java.InMemoryJavaOutputFileObject
import org.jetbrains.intellij.build.io.AddDirEntriesMode
import org.jetbrains.intellij.build.io.PackageIndexBuilder
import org.jetbrains.intellij.build.io.writeZipUsingTempFile
import org.jetbrains.kotlin.backend.common.output.OutputFile
import java.nio.ByteBuffer
import java.nio.file.Path
import java.util.*

class OutputSink {
  private var isChanged = false

  @PublishedApi
  @JvmField
  internal val fileToData = TreeMap<String, Any>()

  @Synchronized
  fun registerKotlincOutput(outputFiles: List<OutputFile>) {
    for (file in outputFiles) {
      fileToData.put(file.relativePath, file)
    }
    isChanged = true
  }

  @Synchronized
  internal fun registerJavacOutput(outputs: List<InMemoryJavaOutputFileObject>) {
    for (output in outputs) {
      fileToData.put(output.path, ByteBuffer.wrap(output.content ?: continue))
    }
    isChanged = true
  }

  inline fun findByPackage(packageName: String, recursive: Boolean, consumer: (String, ByteArray, Int, Int) -> Unit) {
    val prefix = packageName.replace('.', '/') + '/'
    for ((key, info) in fileToData.tailMap(prefix)) {
      if (!key.startsWith(prefix)) {
        break
      }
      if (!recursive && key.indexOf('/', startIndex = prefix.length + 1) != -1) {
        continue
      }

      if (info is OutputFile) {
        // kotlin can produce `.kotlin_module` files
        if (info.relativePath.endsWith(".class")) {
          val data = info.asByteArray()
          consumer(key, data, 0, data.size)
        }
      }
      else {
        val data = (info as ByteBuffer).array()
        consumer(key, data, 0, data.size)
      }
    }
  }

  suspend fun writeAbi(channel: SendChannel<JarContentToProcess>) {
    for ((path, info) in fileToData.entries) {
      val name = path.toByteArray()
      if (info is OutputFile) {
        channel.send(JarContentToProcess(
          name = name,
          data = ByteBuffer.wrap(info.asByteArray()),
          isKotlinModuleMetadata = false,
          isKotlin = true,
        ))
      }
      else {
        channel.send(JarContentToProcess(
          name = name,
          data = (info as ByteBuffer),
          isKotlinModuleMetadata = false,
          isKotlin = false,
        ))
      }
    }
  }

  fun writeToZip(outJar: Path) {
    val packageIndexBuilder = PackageIndexBuilder()
    writeZipUsingTempFile(outJar, packageIndexBuilder.indexWriter) { stream ->
      for ((path, info) in fileToData.entries) {
        packageIndexBuilder.addFile(name = path, addClassDir = false)
        val name = path.toByteArray()
        if (info is ByteBuffer) {
          stream.writeDataRawEntryWithoutCrc(name = name, data = info.slice())
        }
        else {
          stream.writeDataRawEntryWithoutCrc(name = name, data = ByteBuffer.wrap((info as OutputFile).asByteArray()))
        }
      }
      packageIndexBuilder.writePackageIndex(stream = stream, addDirEntriesMode = AddDirEntriesMode.RESOURCE_ONLY)
    }
  }

  @Synchronized
  fun remove(path: String): Boolean {
    val removed = fileToData.remove(path) != null
    if (removed) {
      isChanged = true
    }
    return removed
  }

  @Synchronized
  fun removeAll(paths: Array<String>) {
    for (path in paths) {
      if (fileToData.remove(path) != null) {
        isChanged = true
      }
    }
  }
}