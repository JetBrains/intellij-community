// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnstableApiUsage", "ReplaceGetOrSet")

package org.jetbrains.bazel.jvm.jps.output

import com.intellij.util.lang.HashMapZipFile
import com.intellij.util.lang.ImmutableZipEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.bazel.jvm.jps.java.InMemoryJavaOutputFileObject
import org.jetbrains.intellij.build.io.AddDirEntriesMode
import org.jetbrains.intellij.build.io.PackageIndexBuilder
import org.jetbrains.intellij.build.io.ZipArchiveOutputStream
import org.jetbrains.intellij.build.io.writeZipUsingTempFile
import org.jetbrains.kotlin.backend.common.output.OutputFile
import java.io.File
import java.nio.file.Path
import java.util.*
import java.util.zip.ZipEntry

@PublishedApi
internal class KotlinOutputData(@JvmField val data: ByteArray)

class OutputSink internal constructor(
  @PublishedApi
  @JvmField
  internal val fileToData: TreeMap<String, Any>,
  internal val abiFileToData: TreeMap<String, Any>?,
  @PublishedApi
  @JvmField
  internal val oldZipFile: HashMapZipFile?,
  internal val oldAbiZipFile: HashMapZipFile?,
) : AutoCloseable {
  internal var isChanged: Boolean = false
    private set

  private val javaAbiHelper = if (abiFileToData == null) null else JavaIncrementalAbiHelper()

  override fun close() {
    try {
      oldZipFile?.close()
    }
    finally {
      oldAbiZipFile?.close()
    }
  }

  @Suppress("DuplicatedCode")
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

  @Suppress("DuplicatedCode")
  @Synchronized
  fun registerKotlincAbiOutput(outputFiles: List<OutputFile>) {
    var isChanged = isChanged
    val abiFileToData = abiFileToData!!
    for (file in outputFiles) {
      // not clear - is path system-independent or not?
      val path = file.relativePath.replace(File.separatorChar, '/')
      val newContent = file.asByteArray()
      val oldContent = abiFileToData.put(path, KotlinOutputData(newContent)) as KotlinOutputData?
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
    val abiFileToData = abiFileToData
    val javaAbiHelper = javaAbiHelper
    for (output in outputs) {
      val path = output.path
      // missing content is an error and checked by BazelJpsJavacFileProvider.registerOutputs
      val newContent = output.content!!
      val old = fileToData.put(path, newContent) as? ByteArray
      if (!isChanged && (old == null || !old.contentEquals(newContent))) {
        isChanged = true
      }

      if (javaAbiHelper != null) {
        abiFileToData!!.put(path, javaAbiHelper.createAbiForJava(newContent))
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

  fun writeToZip(outJar: Path) {
    val packageIndexBuilder = PackageIndexBuilder()
    writeZipUsingTempFile(outJar, packageIndexBuilder.indexWriter) { stream ->
      doWriteToZip(oldZipFile = oldZipFile, fileToData = fileToData, packageIndexBuilder = packageIndexBuilder, stream = stream)
      packageIndexBuilder.writePackageIndex(stream = stream, addDirEntriesMode = AddDirEntriesMode.RESOURCE_ONLY)

      // now, close the old files, before writing to it
      close()
    }
  }

  suspend fun createOutputAndAbi(
    outJar: Path,
    abiJar: Path,
  ) {
    withContext(Dispatchers.IO) {
      launch {
        writeToZip(outJar)
      }

      launch {
        writeZipUsingTempFile(file = abiJar, indexWriter = null) { stream ->
          doWriteToZip(oldZipFile = oldAbiZipFile, fileToData = abiFileToData!!, packageIndexBuilder = null, stream = stream)

          // now, close the old file before writing to it
          oldAbiZipFile?.close()
        }
      }
    }
  }

  @Synchronized
  fun remove(path: String) {
    val isOutChanged = fileToData.remove(path) != null
    val isAbiChanged = abiFileToData != null && abiFileToData.remove(path) != null
    if (isOutChanged || isAbiChanged) {
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

private fun doWriteToZip(
  oldZipFile: HashMapZipFile?,
  fileToData: TreeMap<String, Any>,
  packageIndexBuilder: PackageIndexBuilder?,
  stream: ZipArchiveOutputStream,
) {
  for ((path, info) in fileToData.entries) {
    packageIndexBuilder?.addFile(name = path, addClassDir = false)
    val name = path.toByteArray()
    when (info) {
      is ByteArray -> {
        val data = info

        //classChannel?.send(JarContentToProcess(
        //  name = name,
        //  data = data,
        //  isKotlinModuleMetadata = false,
        //  isKotlin = false,
        //))
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

        //if (classChannel != null) {
        //  val isClass = path.endsWith(".class")
        //  val isKotlinMetadata = !isClass && path.endsWith(".kotlin_module")
        //  if (isClass || isKotlinMetadata) {
        //    val sourceFile = outputToSource.get(path)
        //    if (sourceFile == null) {
        //      throw IllegalStateException("No source file for $path")
        //    }
        //
        //    val data = info.getData(hashMapZipFile)
        //    classChannel.send(JarContentToProcess(
        //      name = name,
        //      data = data,
        //      isKotlinModuleMetadata = isKotlinMetadata,
        //      isKotlin = sourceFile.endsWith(".kt"),
        //    ))
        //  }
        //}
      }

      else -> {
        val data = (info as KotlinOutputData).data

        //if (classChannel != null) {
        //  val isClass = path.endsWith(".class")
        //  val isKotlinMetadata = !isClass && path.endsWith(".kotlin_module")
        //  if (isClass || isKotlinMetadata) {
        //    classChannel.send(JarContentToProcess(
        //      name = name,
        //      data = data,
        //      isKotlinModuleMetadata = isKotlinMetadata,
        //      isKotlin = true,
        //    ))
        //  }
        //}

        stream.writeDataRawEntryWithoutCrc(name = name, data = data)
      }
    }
  }
}