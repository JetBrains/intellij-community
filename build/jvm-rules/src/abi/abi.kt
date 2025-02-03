// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm.abi

import kotlinx.coroutines.channels.Channel
import org.jetbrains.intellij.build.io.ZipArchiveOutputStream
import org.jetbrains.intellij.build.io.writeZipUsingTempFile
import org.jetbrains.org.objectweb.asm.*
import java.nio.ByteBuffer
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
suspend fun writeAbi(abiJar: Path, classChannel: Channel<JarContentToProcess>) {
  writeZipUsingTempFile(abiJar, indexWriter = null) { stream ->
    val classesToBeDeleted = HashSet<String>()
    var kotlinModuleMetadata: JarContentToProcess? = null
    for (item in classChannel) {
      if (item.isKotlinModuleMetadata) {
        kotlinModuleMetadata = item
        continue
      }

      val abiData = createAbi(item, classesToBeDeleted) ?: continue
      stream.writeDataRawEntryWithoutCrc(ByteBuffer.wrap(abiData), item.name)
    }

    if (kotlinModuleMetadata != null) {
      writeKotlinModuleMetadata(kotlinModuleMetadata, classesToBeDeleted, stream)
    }
  }
}

private fun createAbi(item: JarContentToProcess, classesToBeDeleted: HashSet<String>): ByteArray? {
  if (item.isKotlin) {
    // check that Java ABI works
    if (true) {
      return item.data
    }
    return createAbForKotlin(classesToBeDeleted, item)
  }
  else {
    val classWriter = ClassWriter(0)
    val abiClassVisitor = JavaAbiClassVisitor(classWriter, classesToBeDeleted)
    ClassReader(item.data).accept(abiClassVisitor, ClassReader.SKIP_FRAMES or ClassReader.SKIP_CODE)
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
  val parsed = requireNotNull(KotlinModuleMetadata.read(kotlinModuleMetadata.data)) {
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

  val newData = if (isChanged) parsed.write() else kotlinModuleMetadata.data
  stream.writeDataRawEntryWithoutCrc(ByteBuffer.wrap(newData), kotlinModuleMetadata.name)
}