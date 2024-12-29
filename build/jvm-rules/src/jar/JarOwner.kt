// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm.kotlin

import org.jetbrains.intellij.build.io.W_OVERWRITE
import org.jetbrains.intellij.build.io.ZipArchiveOutputStream
import org.jetbrains.intellij.build.io.ZipIndexWriter
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.file.Path
import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import org.jetbrains.kotlin.backend.common.output.OutputFileCollection
import org.jetbrains.kotlin.name.FqName
import java.nio.channels.FileChannel
import java.nio.file.Files

data class JarOwner(
  @JvmField val jar: Path,
  @JvmField val label: String? = null,
  @JvmField val aspect: String? = null,
) {
  companion object {
    // These attributes are used by JavaBuilder, Turbine, and ijar.
    // They must all be kept in sync.
    @JvmField
    val TARGET_LABEL = Attributes.Name("Target-Label")

    @JvmField
    val INJECTING_RULE_KIND = Attributes.Name("Injecting-Rule-Kind")
  }
}

private val CREATED_BY = Attributes.Name("Created-By")
private val MANIFEST_NAME_BYTES = JarFile.MANIFEST_NAME.toByteArray()

private fun writeManifest(manifest: Manifest, out: ZipArchiveOutputStream) {
  //packageIndexBuilder.addFile(JarFile.MANIFEST_NAME)

  val manifestOut = ByteArrayOutputStream()
  manifest.write(manifestOut)

  val data = manifestOut.toByteArray()
  val size = data.size
  out.writeDataRawEntry(
    data = ByteBuffer.wrap(data),
    name = MANIFEST_NAME_BYTES,
    size = size,
    compressedSize = size,
    method = ZipEntry.STORED,
    crc = 0,
  )
}

fun createJar(
  outFile: Path,
  outputFiles: OutputFileCollection,
  targetLabel: String?,
  mainClass: FqName?,
) {
  // we should try to create the output dir first
  outFile.parent?.let {
    Files.createDirectories(it)
  }
  ZipArchiveOutputStream(
    channel = FileChannel.open(outFile, W_OVERWRITE),
    zipIndexWriter = ZipIndexWriter(indexWriter = null),
  ).use { out ->

    val manifest = Manifest()
    val attributes = manifest.mainAttributes
    attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0")
    attributes.put(CREATED_BY, "io.bazel.rules.kotlin")
    attributes[JarOwner.INJECTING_RULE_KIND] = "jvm_library"
    targetLabel?.let {
      attributes[JarOwner.TARGET_LABEL] = targetLabel
    }
    if (mainClass != null) {
      attributes.put(Attributes.Name.MAIN_CLASS, mainClass.asString())
    }
    writeManifest(manifest = manifest, out = out)

    for (outputFile in outputFiles.asList()) {
      val data = outputFile.asByteArray()
      out.writeDataRawEntry(
        data = ByteBuffer.wrap(data),
        name = outputFile.relativePath.toByteArray(),
        size = data.size,
        compressedSize = data.size,
        method = ZipEntry.STORED,
        crc = 0,
      )
    }
  }
}
