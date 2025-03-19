// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnstableApiUsage", "ReplaceGetOrSet")

package org.jetbrains.bazel.jvm.jps.output

import androidx.collection.MutableScatterSet
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
import org.jetbrains.kotlin.build.GeneratedFile
import java.io.File
import java.nio.file.Path
import java.util.*
import java.util.zip.ZipEntry

internal const val ABI_IC_NODE_FORMAT_VERSION: Int = 1

class OutputSink internal constructor(
  @PublishedApi
  @JvmField
  internal val fileToData: TreeMap<String, Any>,
  abiFileToData: TreeMap<String, Any>?,
  @PublishedApi
  @JvmField
  internal val oldZipFile: HashMapZipFile?,
  oldAbiZipFile: HashMapZipFile?,
) : AutoCloseable {
  internal var isChanged: Boolean = false
    private set

  private val abiHelper = if (abiFileToData == null) null else IncrementalAbiHelper(abiFileToData, oldAbiZipFile)

  override fun close() {
    try {
      oldZipFile?.close()
    }
    finally {
      abiHelper?.close()
    }
  }

  @Suppress("DuplicatedCode")
  @Synchronized
  fun registerKotlincOutput(outputFiles: List<OutputFile>) {
    for (file in outputFiles) {
      // not clear - is path system-independent or not?
      fileToData.put(file.relativePath.replace(File.separatorChar, '/'), file.asByteArray())
    }
    isChanged = true
  }

  @Suppress("DuplicatedCode")
  @Synchronized
  fun registerIncrementalKotlincOutput(outputFiles: List<GeneratedFile>) {
    var isChanged = isChanged
    for (file in outputFiles) {
      val newContent = file.data
      val oldContent = fileToData.put(file.relativePath, newContent)
      if (!isChanged && (oldContent == null || !compareContent(oldContent, newContent))) {
        isChanged = true
      }
    }
    if (isChanged) {
      this.isChanged = true
    }
  }

  private fun compareContent(oldContent: Any, newContent: ByteArray): Boolean {
    return when (oldContent) {
      is ByteArray -> oldContent.contentEquals(newContent)
      is ImmutableZipEntry -> oldContent.getData(oldZipFile!!).contentEquals(newContent)
      else -> false
    }
  }

  fun getData(path: String): ByteArray? {
    val info = fileToData.get(path) ?: return null
    return when (info) {
      is ImmutableZipEntry -> info.getData(oldZipFile!!)
      else -> info as ByteArray
    }
  }

  fun getSize(path: String): Int {
    val info = fileToData.get(path) ?: return -1
    return when (info) {
      is ImmutableZipEntry -> info.uncompressedSize
      else -> (info as ByteArray).size
    }
  }

  @Suppress("DuplicatedCode")
  @Synchronized
  fun registerKotlincAbiOutput(outputFiles: List<OutputFile>) {
    isChanged = abiHelper!!.registerKotlincAbiOutput(outputFiles, isChanged)
  }

  @Synchronized
  internal fun registerJavacOutput(outputs: List<InMemoryJavaOutputFileObject>) {
    var isChanged = isChanged
    val abiHelper = abiHelper
    for (output in outputs) {
      val path = output.path
      // missing content is an error and checked by BazelJpsJavacFileProvider.registerOutputs
      val newContent = output.content!!
      val old = fileToData.put(path, newContent) as? ByteArray
      if (!isChanged && (old == null || !old.contentEquals(newContent))) {
        isChanged = true
      }

      abiHelper?.createAbiForJava(path, newContent)
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

      // kotlin can produce `.kotlin_module` files
      if (!path.endsWith(".class")) {
        continue
      }

      val data = if (info is ImmutableZipEntry) {
        // todo use direct byte buffer
        info.getData(oldZipFile!!)
      }
      else {
        info as ByteArray
      }
      consumer(path, data, 0, data.size)
    }
  }

  inline fun findVfsChildren(parentName: String, dirConsumer: (String) -> Unit, consumer: (String) -> Unit) {
    val prefix = if (parentName.isEmpty()) "" else "$parentName/"
    val dirUniqueGuard = MutableScatterSet<String>()
    for ((path, _) in fileToData.tailMap(prefix)) {
      if (!path.startsWith(prefix)) {
        break
      }

      val nextSlashIndex = path.indexOf('/', startIndex = prefix.length + 1)
      if (nextSlashIndex != -1) {
        val dirName = path.substring(prefix.length, nextSlashIndex)
        if (dirUniqueGuard.add(dirName)) {
          dirConsumer(dirName)
        }
        continue
      }
      else {
        consumer(path)
      }
    }
  }

  fun writeToZip(outJar: Path) {
    val packageIndexBuilder = PackageIndexBuilder(writeCrc32 = false)
    writeZipUsingTempFile(outJar, packageIndexBuilder.indexWriter) { stream ->
      doWriteToZip(oldZipFile = oldZipFile, fileToData = fileToData, packageIndexBuilder = packageIndexBuilder, stream = stream) { _, _, _ ->}
      packageIndexBuilder.writePackageIndex(stream = stream, addDirEntriesMode = AddDirEntriesMode.RESOURCE_ONLY)

      // now, close the old file, before writing to it
      oldZipFile?.close()
    }
  }

  suspend fun createOutputAndAbi(outJar: Path, abiJar: Path) {
    withContext(Dispatchers.IO) {
      launch {
        writeToZip(outJar)
      }

      launch {
        abiHelper!!.write(abiJar)
      }
    }
  }

  @Synchronized
  fun remove(path: String) {
    val isOutChanged = fileToData.remove(path) != null
    val isAbiChanged = abiHelper?.remove(path) ?: false
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

internal inline fun doWriteToZip(
  oldZipFile: HashMapZipFile?,
  fileToData: TreeMap<String, Any>,
  packageIndexBuilder: PackageIndexBuilder?,
  stream: ZipArchiveOutputStream,
  crossinline newDataProcessor: (ByteArray, String, ByteArray) -> Unit,
) {
  for ((path, info) in fileToData.entries) {
    packageIndexBuilder?.addFile(name = path, addClassDir = false)
    val name = path.toByteArray()
    if (info is ImmutableZipEntry) {
      val hashMapZipFile = oldZipFile!!
      val buffer = info.getByteBuffer(hashMapZipFile, null)
      try {
        val size = info.uncompressedSize
        stream.writeDataRawEntry(name = name, data = buffer, crc = 0, size = size, compressedSize = size, method = ZipEntry.STORED)
      }
      finally {
        hashMapZipFile.releaseBuffer(buffer)
      }
    }
    else {
      val data = info as ByteArray
      stream.writeDataRawEntryWithoutCrc(name = name, data = data)
      newDataProcessor(data, path, name)
    }
  }
}