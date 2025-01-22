// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnstableApiUsage")

package org.jetbrains.bazel.jvm.jps

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.bazel.jvm.abi.JarContentToProcess
import org.jetbrains.bazel.jvm.abi.writeAbi
import org.jetbrains.intellij.build.io.*
import java.nio.file.NoSuchFileException
import java.nio.file.Path

val emptyStringArray: Array<String> = emptyArray()

data class SourceDescriptor(
  // absolute and normalized
  @JvmField var sourceFile: Path,
  @JvmField var digest: ByteArray,
  @JvmField var outputs: Array<String>,
  @JvmField var isChanged: Boolean,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is SourceDescriptor) return false

    if (sourceFile != other.sourceFile) return false
    if (!digest.contentEquals(other.digest)) return false
    if (!outputs.contentEquals(other.outputs)) return false

    return true
  }

  override fun hashCode(): Int {
    var result = sourceFile.hashCode()
    result = 31 * result + (digest.contentHashCode())
    result = 31 * result + outputs.contentHashCode()
    return result
  }
}

suspend fun packageToJar(
  outJar: Path,
  abiJar: Path?,
  sourceDescriptors: Array<SourceDescriptor>,
  classOutDir: Path,
  span: Span,
) {
  //var abiJar = Path.of(outJar.toString() + ".abi.jar")
  if (abiJar == null) {
    withContext(Dispatchers.IO) {
      createJar(
        outJar = outJar,
        sourceDescriptors = sourceDescriptors,
        classOutDir = classOutDir,
        abiChannel = null,
        span = span,
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
        span = span,
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
  span: Span,
) {
  val packageIndexBuilder = PackageIndexBuilder()
  writeZipUsingTempFile(outJar, packageIndexBuilder.indexWriter) { stream ->
    // output file maybe associated with more than one output file
    val uniqueGuard = hashSet<String>(sourceDescriptors.size + 10)

    for (sourceDescriptor in sourceDescriptors) {
      val isKotlin = sourceDescriptor.sourceFile.toString().endsWith(".kt")
      for (path in sourceDescriptor.outputs) {
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
                isKotlin = isKotlin,
              ))
              continue
            }
          }

          stream.file(nameString = path, file = file)
        }
        catch (_: NoSuchFileException) {
          span.addEvent(
            "output file exists in src-to-output mapping, but not found on disk",
            Attributes.of(
              AttributeKey.stringKey("path"), path,
              AttributeKey.stringKey("classOutDir"), classOutDir.toString()
            ),
          )
        }
      }
    }
    packageIndexBuilder.writePackageIndex(stream = stream, addDirEntriesMode = AddDirEntriesMode.RESOURCE_ONLY)
  }
}


