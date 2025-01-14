@file:Suppress("UnstableApiUsage")

package org.jetbrains.bazel.jvm.jps

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.bazel.jvm.abi.writeAbi
import org.jetbrains.intellij.build.io.*
import java.nio.file.NoSuchFileException
import java.nio.file.Path

class SourceDescriptor(
  // absolute and normalized
  @JvmField var sourceFile: Path,
  @JvmField var digest: ByteArray? = null,
  @JvmField var outputs: List<String>? = null,
) {
  //fun isEmpty(): Boolean = digest == null && outputs.isNullOrEmpty()
}

suspend fun packageToJar(
  outJar: Path,
  abiJar: Path?,
  sourceDescriptors: Array<SourceDescriptor>,
  classOutDir: Path,
  log: RequestLog
) {
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

  val classChannel = Channel<Pair<ByteArray, ByteArray>>(capacity = 8)
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
  abiChannel: Channel<Pair<ByteArray, ByteArray>>?,
  messageHandler: RequestLog,
) {
  val packageIndexBuilder = PackageIndexBuilder()
  writeZipUsingTempFile(outJar, packageIndexBuilder.indexWriter) { stream ->
    // output file maybe associated with more than one output file
    val uniqueGuard = HashSet<String>(sourceDescriptors.size + 10)

    for (sourceDescriptor in sourceDescriptors) {
      for (path in sourceDescriptor.outputs ?: continue) {
        // duplicated - ignore it
        if (!uniqueGuard.add(path)) {
          continue
        }

        packageIndexBuilder.addFile(name = path, addClassDir = false)
        try {
          val file = classOutDir.resolve(path)
          if (abiChannel != null && path.endsWith(".class")) {
            val name = path.toByteArray()
            val classData = stream.fileAndGetData(name, file)
            abiChannel.send(name to classData)
          }
          else {
            stream.file(nameString = path, file = file)
          }
        }
        catch (_: NoSuchFileException) {
          messageHandler.warn("output file exists in src-to-output mapping, but not found on disk: $path (classOutDir=$classOutDir)")
        }
      }
    }
    packageIndexBuilder.writePackageIndex(stream = stream, addDirEntriesMode = AddDirEntriesMode.RESOURCE_ONLY)
  }
}


