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
import java.util.zip.ZipEntry

@PublishedApi
internal class KotlinOutputData(@JvmField val data: ByteArray)

class OutputSink private constructor(
  @PublishedApi
  @JvmField
  internal val fileToData: TreeMap<String, Any>,
  @PublishedApi
  @JvmField
  internal val oldZipFile: HashMapZipFile?,
) : AutoCloseable {
  internal var isChanged: Boolean = false
    private set

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
      return OutputSink(fileToData = fileToData, oldZipFile = zipFile)
    }
  }

  override fun close() {
    oldZipFile?.close()
  }

  @Synchronized
  fun registerKotlincOutput(outputFiles: List<OutputFile>) {
    var isChanged = isChanged
    for (file in outputFiles) {
      // not clear - is path system-independent or not?
      val path = file.relativePath.replace(File.separatorChar, '/')
      val newContent = file.asByteArray()
      val oldContent = fileToData.put(path, KotlinOutputData(newContent)) as KotlinOutputData?
      if (!isChanged && (oldContent == null || !oldContent.data.contentEquals(newContent))) {
        isChanged = true
      }
    }
    if (isChanged) {
      this.isChanged = true
    }
  }

  @Synchronized
  internal fun registerJavacOutput(outputs: List<InMemoryJavaOutputFileObject>) {
    var isChanged = isChanged
    for (output in outputs) {
      val path = output.path
      // missing content is an error and checked by BazelJpsJavacFileProvider.registerOutputs
      val newContent = output.content!!
      val old = fileToData.put(path, newContent) as? ByteArray
      if (!isChanged && (old == null || !old.contentEquals(newContent))) {
        isChanged = true
      }
    }
    if (isChanged) {
      this.isChanged = true
    }
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

      val data: ByteArray
      if (info is KotlinOutputData) {
        // kotlin can produce `.kotlin_module` files
        if (path.endsWith(".class")) {
          data = info.data
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
        when (info) {
          is ByteArray -> {
            val data = info

            classChannel?.send(JarContentToProcess(
              name = name,
              data = data,
              isKotlinModuleMetadata = false,
              isKotlin = false,
            ))
            stream.writeDataRawEntryWithoutCrc(name = name, data = data)
          }

          is ImmutableZipEntry -> {
            val hashMapZipFile = oldZipFile!!
            val buffer = info.getByteBuffer(hashMapZipFile, null)
            try {
              val size = buffer.remaining()
              stream.writeDataRawEntry(name = name, data = buffer, crc = 0, size = size, compressedSize = size, method = ZipEntry.STORED)
            }
            finally {
              hashMapZipFile.releaseBuffer(buffer)
            }

            if (classChannel != null) {
              val isClass = path.endsWith(".class")
              val isKotlinMetadata = !isClass && path.endsWith(".kotlin_module")
              if (isClass || isKotlinMetadata) {
                val sourceFile = outputToSource.get(path)
                if (sourceFile == null) {
                  throw IllegalStateException("No source file for $path")
                }

                val data = info.getData(hashMapZipFile)
                classChannel.send(JarContentToProcess(
                  name = name,
                  data = data,
                  isKotlinModuleMetadata = isKotlinMetadata,
                  isKotlin = sourceFile.endsWith(".kt"),
                ))
              }
            }
          }

          else -> {
            val data = (info as KotlinOutputData).data

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

            stream.writeDataRawEntryWithoutCrc(name = name, data = data)
          }
        }
      }
      packageIndexBuilder.writePackageIndex(stream = stream, addDirEntriesMode = AddDirEntriesMode.RESOURCE_ONLY)
    }
  }

  @Synchronized
  fun remove(path: String) {
    if (fileToData.remove(path) != null) {
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