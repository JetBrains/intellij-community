// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnstableApiUsage", "ReplaceGetOrSet")

package org.jetbrains.bazel.jvm.jps

import com.intellij.util.lang.HashMapZipFile
import com.intellij.util.lang.ImmutableZipEntry
import kotlinx.coroutines.channels.Channel
import org.jetbrains.bazel.jvm.abi.JarContentToProcess
import org.jetbrains.bazel.jvm.jps.java.InMemoryJavaOutputFileObject
import org.jetbrains.intellij.build.io.AddDirEntriesMode
import org.jetbrains.intellij.build.io.PackageIndexBuilder
import org.jetbrains.intellij.build.io.writeZipUsingTempFile
import org.jetbrains.kotlin.backend.common.output.OutputFile
import java.io.File
import java.nio.file.Path
import java.util.*

class OutputSink private constructor(
  @PublishedApi
  @JvmField
  internal val fileToData: TreeMap<String, Any>,
  @PublishedApi
  @JvmField
  internal val oldZipFile: HashMapZipFile?,
) : AutoCloseable {
  private var isChanged = false

  //@PublishedApi
  //@JvmField
  //internal val removedFiles = hashSet<String>()

  companion object {
    fun createOutputSink(oldJar: Path?): OutputSink {
      // read data from old JAR if exists to copy data
      val zipFile = (if (oldJar == null) null else HashMapZipFile.loadIfNotEmpty(oldJar)) ?: return OutputSink(TreeMap(), oldZipFile = null)
      val fileToData = TreeMap<String, Any>()
      var ok = false
      try {
        for (entry in zipFile.entries) {
          if (entry.isDirectory) {
            continue
          }

          val name = entry.name
          if (name != "__index__") {
            fileToData.put(name, entry)
          }
        }
        ok = true
      }
      finally {
        if (!ok) {
          zipFile.close()
        }
      }
      return OutputSink(fileToData, oldZipFile = zipFile)
    }
  }

  override fun close() {
    oldZipFile?.close()
  }

  @Synchronized
  fun registerKotlincOutput(outputFiles: List<OutputFile>) {
    for (file in outputFiles) {
      // not clear - is path system-independent or not?
      val path = file.relativePath.replace(File.separatorChar, '/')
      //removedFiles.remove(path)
      fileToData.put(path, file)
    }
    isChanged = true
  }

  @Synchronized
  internal fun registerJavacOutput(outputs: List<InMemoryJavaOutputFileObject>) {
    for (output in outputs) {
      val path = output.path
      //removedFiles.remove(path)
      // missing content is an error and checked by BazelJpsJavacFileProvider.registerOutputs
      fileToData.put(path, output.content!!)
    }
    isChanged = true
  }

  inline fun findByPackage(packageName: String, recursive: Boolean, consumer: (String, ByteArray, Int, Int) -> Unit) {
    val prefix = packageName.replace('.', '/') + '/'
    for ((path, info) in fileToData.tailMap(prefix)) {
      if (!path.startsWith(prefix)) {
        break
      }
      if (!recursive && path.indexOf('/', startIndex = prefix.length + 1) != -1) {
        continue
      }

      //if (removedFiles.contains(path)) {
      //  continue
      //}

      val data: ByteArray
      if (info is OutputFile) {
        // kotlin can produce `.kotlin_module` files
        if (path.endsWith(".class")) {
          data = info.asByteArray()
        }
        else {
          continue
        }
      }
      else if (info is ImmutableZipEntry) {
        // todo use direct byte buffer
        data = info.getData(oldZipFile!!)
      }
      else {
        data = info as ByteArray
      }
      consumer(path, data, 0, data.size)
    }
  }

  // we should implement a more optimized solution without classChannel later (incremental update of JAR on disk)
  suspend fun writeToZip(outJar: Path, classChannel: Channel<JarContentToProcess>?, outputToSource: Map<String, String>) {
    val packageIndexBuilder = PackageIndexBuilder()
    writeZipUsingTempFile(outJar, packageIndexBuilder.indexWriter) { stream ->
      for ((path, info) in fileToData.entries) {
        packageIndexBuilder.addFile(name = path, addClassDir = false)
        val name = path.toByteArray()
        val data: ByteArray
        if (info is ByteArray) {
          data = info

          classChannel?.send(JarContentToProcess(
            name = name,
            data = data,
            isKotlinModuleMetadata = false,
            isKotlin = false,
          ))
        }
        else if (info is ImmutableZipEntry) {
          // todo use direct byte buffer
          data = info.getData(oldZipFile!!)

          if (classChannel != null) {
            val isClass = path.endsWith(".class")
            val isKotlinMetadata = !isClass && path.endsWith(".kotlin_module")
            if (isClass || isKotlinMetadata) {
              val sourceFile = outputToSource.get(path)
              if (sourceFile == null) {
                throw IllegalStateException("No source file for $path")
              }

              classChannel.send(JarContentToProcess(
                name = name,
                data = data,
                isKotlinModuleMetadata = isKotlinMetadata,
                isKotlin = sourceFile.endsWith(".kt"),
              ))
            }
          }
        }
        else {
          data = (info as OutputFile).asByteArray()

          if (classChannel != null) {
            val isClass = path.endsWith(".class")
            val isKotlinMetadata = !isClass && path.endsWith(".kotlin_module")
            if (isClass || isKotlinMetadata) {
              classChannel.send(JarContentToProcess(
                name = name,
                data = data,
                isKotlinModuleMetadata = isKotlinMetadata,
                isKotlin = true,
              ))
            }
          }
        }

        stream.writeDataRawEntryWithoutCrc(name = name, data = data)
      }
      packageIndexBuilder.writePackageIndex(stream = stream, addDirEntriesMode = AddDirEntriesMode.RESOURCE_ONLY)
    }
  }

  @Synchronized
  fun remove(path: String) {
    if (fileToData.remove(path) == null) {
      //removedFiles.add(path)
    }
    else {
      isChanged = true
    }
  }

  @Synchronized
  fun removeAll(paths: Array<String>) {
    for (path in paths) {
      remove(path)
    }
  }
}