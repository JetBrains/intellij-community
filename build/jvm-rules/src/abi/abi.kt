// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm.abi

import kotlinx.coroutines.channels.ReceiveChannel
import org.jetbrains.intellij.build.io.ZipArchiveOutputStream
import org.jetbrains.intellij.build.io.writeZipUsingTempFile
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.ClassWriter
import java.nio.file.Path
import kotlin.metadata.jvm.KotlinModuleMetadata
import kotlin.metadata.jvm.UnstableMetadataApi

class JarContentToProcess(
  @JvmField val name: ByteArray,
  @JvmField val data: ByteArray,
  @JvmField val isKotlinModuleMetadata: Boolean,
  @JvmField val isKotlin: Boolean,
)

@OptIn(UnstableMetadataApi::class)
suspend fun writeAbi(abiJar: Path, classChannel: ReceiveChannel<JarContentToProcess>) {
  writeZipUsingTempFile(abiJar, indexWriter = null) { stream ->
    val classesToBeDeleted = HashSet<String>()
    var kotlinModuleMetadata: JarContentToProcess? = null
    for (item in classChannel) {
      if (item.isKotlinModuleMetadata) {
        kotlinModuleMetadata = item
        continue
      }

      val abiData = createAbi(item, classesToBeDeleted) ?: continue
      stream.writeDataRawEntryWithoutCrc(item.name, abiData)
    }

    if (kotlinModuleMetadata != null) {
      writeKotlinModuleMetadata(kotlinModuleMetadata, classesToBeDeleted, stream)
    }
  }
}

private fun createAbi(item: JarContentToProcess, classesToBeDeleted: HashSet<String>): ByteArray? {
  val data = item.data
  if (item.isKotlin) {
    // check that Java ABI works
    //if (true) {
      return data
    //}
    //return createAbForKotlin(classesToBeDeleted, item)
  }
  else {
    val classWriter = ClassWriter(0)
    val abiClassVisitor = JavaAbiClassVisitor(classWriter, classesToBeDeleted)
    ClassReader(data).accept(abiClassVisitor, ClassReader.SKIP_FRAMES or ClassReader.SKIP_CODE)
    if (abiClassVisitor.isApiClass) {
      return classWriter.toByteArray()
    }
  }
  return null
}

@OptIn(UnstableMetadataApi::class)
private fun writeKotlinModuleMetadata(
  kotlinModuleMetadata: JarContentToProcess,
  classesToBeDeleted: HashSet<String>,
  stream: ZipArchiveOutputStream
) {
  val bytes = kotlinModuleMetadata.data
  val parsed = requireNotNull(KotlinModuleMetadata.read(bytes)) {
    "Unsuccessful parsing of Kotlin module metadata for ABI generation: ${kotlinModuleMetadata.name.decodeToString()}"
  }

  val iterator = parsed.kmModule.packageParts.iterator()
  var isChanged = false
  while (iterator.hasNext()) {
    val (_, kmPackageParts) = iterator.next()

    kmPackageParts.fileFacades.removeIf { it in classesToBeDeleted }
    if (kmPackageParts.fileFacades.isEmpty() && kmPackageParts.multiFileClassParts.isEmpty()) {
      iterator.remove()
      isChanged = true
      continue
    }
  }

  val newData = if (isChanged) parsed.write() else bytes
  stream.writeDataRawEntryWithoutCrc(kotlinModuleMetadata.name, newData)
}