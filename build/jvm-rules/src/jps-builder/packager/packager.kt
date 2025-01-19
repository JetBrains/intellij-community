// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnstableApiUsage")

package org.jetbrains.bazel.jvm.jps

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.bazel.jvm.abi.JarContentToProcess
import org.jetbrains.bazel.jvm.abi.writeAbi
import org.jetbrains.intellij.build.io.*
import java.nio.file.NoSuchFileException
import java.nio.file.Path

data class SourceDescriptor(
  // absolute and normalized
  @JvmField var sourceFile: Path,
  @JvmField var digest: ByteArray? = null,
  @JvmField var outputs: List<String>? = null,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is SourceDescriptor) return false

    if (sourceFile != other.sourceFile) return false
    if (!digest.contentEquals(other.digest)) return false
    if (outputs != other.outputs) return false

    return true
  }

  override fun hashCode(): Int {
    var result = sourceFile.hashCode()
    result = 31 * result + (digest?.contentHashCode() ?: 0)
    result = 31 * result + (outputs?.hashCode() ?: 0)
    return result
  }
}

suspend fun packageToJar(
  outJar: Path,
  abiJar: Path?,
  sourceDescriptors: Array<SourceDescriptor>,
  classOutDir: Path,
  log: RequestLog
) {
  //var abiJar = Path.of(outJar.toString() + ".abi.jar")
  if (abiJar == null) {
    withContext(Dispatchers.IO) {
      createJar(
        outJar = outJar,
        sourceDescriptors = sourceDescriptors,
        classOutDir = classOutDir,
        abiChannel = null,
        messageHandler = log,
      )
    }
    return
  }

  val classChannel = Channel<JarContentToProcess>(capacity = 8)
  withContext(Dispatchers.IO) {
    launch {
      createJar(
        outJar = outJar,
        sourceDescriptors = sourceDescriptors,
        classOutDir = classOutDir,
        abiChannel = classChannel,
        messageHandler = log,
      )
      classChannel.close()
    }

    writeAbi(abiJar, classChannel)
    //if (classesToBeDeleted.isNotEmpty()) {
    //  messageHandler.debug("Non-abi classes to be deleted: ${classesToBeDeleted.size}")
    //}
  }
}

private suspend fun createJar(
  outJar: Path,
  sourceDescriptors: Array<SourceDescriptor>,
  classOutDir: Path,
  abiChannel: Channel<JarContentToProcess>?,
  messageHandler: RequestLog,
) {
  val packageIndexBuilder = PackageIndexBuilder()
  writeZipUsingTempFile(outJar, packageIndexBuilder.indexWriter) { stream ->
    // output file maybe associated with more than one output file
    val uniqueGuard = hashSet<String>(sourceDescriptors.size + 10)

    for (sourceDescriptor in sourceDescriptors) {
      for (path in sourceDescriptor.outputs ?: continue) {
        // duplicated - ignore it
        if (!uniqueGuard.add(path)) {
          continue
        }

        packageIndexBuilder.addFile(name = path, addClassDir = false)
        try {
          val file = classOutDir.resolve(path)
          if (abiChannel != null) {
            val isClass = path.endsWith(".class")
            val isKotlinMetadata = !isClass && path.endsWith(".kotlin_module")
            if (isClass || isKotlinMetadata) {
              val name = path.toByteArray()
              val classData = stream.fileAndGetData(name, file)
              abiChannel.send(JarContentToProcess(
                name = name,
                data = classData,
                isKotlinModuleMetadata = isKotlinMetadata,
              ))
              continue
            }
          }

          stream.file(nameString = path, file = file)
        }
        catch (_: NoSuchFileException) {
          messageHandler.warn("output file exists in src-to-output mapping, but not found on disk: $path (classOutDir=$classOutDir)")
        }
      }
    }
    packageIndexBuilder.writePackageIndex(stream = stream, addDirEntriesMode = AddDirEntriesMode.RESOURCE_ONLY)
  }
}


