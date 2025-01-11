@file:Suppress("UnstableApiUsage")

package org.jetbrains.bazel.jvm.jps

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.io.*
import org.jetbrains.jps.incremental.storage.ExperimentalSourceToOutputMapping
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.ClassWriter
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.*
import java.nio.file.attribute.DosFileAttributeView
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.util.zip.ZipEntry


suspend fun packageToJar(
  outJar: Path,
  abiJar: Path?,
  sourceToOutputMap: ExperimentalSourceToOutputMapping,
  classOutDir: Path,
  messageHandler: ConsoleMessageHandler
) {
  if (abiJar == null) {
    withContext(Dispatchers.IO) {
      createJar(
        outJar = outJar,
        sourceToOutputMap = sourceToOutputMap,
        classOutDir = classOutDir,
        abiChannel = null,
        messageHandler = messageHandler,
      )
    }
    return
  }

  val classChannel = Channel<Pair<ByteArray, ByteArray>>(capacity = 8)
  withContext(Dispatchers.IO) {
    launch {
      createJar(
        outJar = outJar,
        sourceToOutputMap = sourceToOutputMap,
        classOutDir = classOutDir,
        abiChannel = classChannel,
        messageHandler = messageHandler,
      )
      classChannel.close()
    }

    writeZipUsingTempFile(abiJar, indexWriter = null) { stream ->
      val classesToBeDeleted = HashSet<String>()
      for ((name, classData) in classChannel) {
        val classWriter = ClassWriter(0)
        val abiClassVisitor = AbiClassVisitor(classVisitor = classWriter, classesToBeDeleted = classesToBeDeleted)
        ClassReader(classData).accept(abiClassVisitor, 0)
        if (!abiClassVisitor.isApiClass) {
          continue
        }

        val abiData = classWriter.toByteArray()
        stream.writeDataRawEntry(ByteBuffer.wrap(abiData), name, abiData.size, abiData.size, ZipEntry.STORED, 0)
      }

      if (classesToBeDeleted.isNotEmpty()) {
        messageHandler.debug("Non-abi classes to be deleted: ${classesToBeDeleted.size}")
      }
    }
  }
}

private suspend fun createJar(
  outJar: Path,
  sourceToOutputMap: ExperimentalSourceToOutputMapping,
  classOutDir: Path,
  abiChannel: Channel<Pair<ByteArray, ByteArray>>?,
  messageHandler: ConsoleMessageHandler,
) {
  val packageIndexBuilder = PackageIndexBuilder()
  writeZipUsingTempFile(outJar, packageIndexBuilder.indexWriter) { stream ->
    // MVStore like a TreeMap, keys already sorted
    //val all = sourceToOutputMap.outputs().toList()
    //val unique = LinkedHashSet(all)
    //if (unique.size != all.size) {
    //  messageHandler.out.appendLine("Duplicated outputs: ${all.groupingBy { it }
    //          .eachCount()
    //          .filter { it.value > 1 }
    //          .keys
    //  }")
    //}

    for (path in sourceToOutputMap.outputs().toList()) {
      // duplicated - ignore it
      if (path.endsWith(".kotlin_module")) {
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
        messageHandler.warn("output file exists in src-to-output mapping, but not found on disk: $path")
      }
    }
    packageIndexBuilder.writePackageIndex(stream = stream, addDirEntriesMode = AddDirEntriesMode.RESOURCE_ONLY)
  }
}

private inline fun writeZipUsingTempFile(file: Path, indexWriter: IkvIndexBuilder?, task: (ZipArchiveOutputStream) -> Unit) {
  val tempFile = Files.createTempFile(file.parent, file.fileName.toString(), ".tmp")
  var moved = false
  try {
    ZipArchiveOutputStream(
      channel = FileChannel.open(tempFile, WRITE),
      zipIndexWriter = ZipIndexWriter(indexWriter),
    ).use {
      task(it)
    }

    try {
      moveAtomic(tempFile, file)
    }
    catch (e: AccessDeniedException) {
      makeFileWritable(file, e)
      moveAtomic(tempFile, file)
    }
    moved = true
  }
  finally {
    if (!moved) {
      Files.deleteIfExists(tempFile)
    }
  }
}

private fun moveAtomic(from: Path, to: Path) {
  try {
    Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
  }
  catch (_: AtomicMoveNotSupportedException) {
    Files.move(from, to, StandardCopyOption.REPLACE_EXISTING)
  }
}

private fun makeFileWritable(file: Path, cause: Throwable) {
  val posixView = Files.getFileAttributeView<PosixFileAttributeView?>(file, PosixFileAttributeView::class.java)
  if (posixView != null) {
    val permissions = posixView.readAttributes().permissions()
    permissions.add(PosixFilePermission.OWNER_WRITE)
    posixView.setPermissions(permissions)
  }

  val dosView = Files.getFileAttributeView<DosFileAttributeView?>(file, DosFileAttributeView::class.java)
  @Suppress("IfThenToSafeAccess")
  if (dosView != null) {
    dosView.setReadOnly(false)
  }

  throw UnsupportedOperationException("Unable to modify file attributes. Unsupported platform.", cause)
}


