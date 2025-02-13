// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("PackageDirectoryMismatch")

package org.jetbrains.kotlin.build

import org.jetbrains.kotlin.incremental.LocalFileKotlinClass
import org.jetbrains.kotlin.metadata.deserialization.MetadataVersion
import java.io.File

open class GeneratedFile(
  sourceFiles: Collection<File>,
  val outputFile: File,
  @JvmField val data: ByteArray,
) {
  val sourceFiles = sourceFiles.sortedBy { it.path }

  override fun toString(): String = "${this::class.java.simpleName}: $outputFile"
}

class GeneratedJvmClass(
  sourceFiles: Collection<File>,
  data: ByteArray,
  outputFile: File,
  metadataVersionFromLanguageVersion: MetadataVersion
) : GeneratedFile(sourceFiles, outputFile, data) {
  val outputClass = requireNotNull(LocalFileKotlinClass.create(outputFile, data, metadataVersionFromLanguageVersion)) {
    "Couldn't load KotlinClass from $outputFile; it may happen because class doesn't have valid Kotlin annotations"
  }
}

private val META_INF_SUFFIX = File.separatorChar + "META-INF"

@Suppress("unused")
fun File.isModuleMappingFile(): Boolean {
  if (!path.endsWith(".kotlin_module")) {
    return false
  }
  val parentPath = parent
  return parentPath == "META-INF" || parentPath.endsWith(META_INF_SUFFIX)
}