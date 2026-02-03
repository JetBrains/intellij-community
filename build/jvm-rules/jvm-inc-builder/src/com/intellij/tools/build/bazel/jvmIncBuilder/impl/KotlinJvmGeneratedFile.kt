// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.impl

import org.jetbrains.kotlin.build.GeneratedFile
import org.jetbrains.kotlin.incremental.LocalFileKotlinClass
import org.jetbrains.kotlin.metadata.deserialization.MetadataVersion
import org.jetbrains.kotlin.utils.sure
import java.io.File

class KotlinJvmGeneratedFile(
  sourceFiles: Collection<File>,
  outputFile: File,
  fileContents: ByteArray,
  metadataVersionFromLanguageVersion: MetadataVersion,
) : GeneratedFile(sourceFiles, outputFile) {
  val outputClass = LocalFileKotlinClass.create(outputFile, fileContents, metadataVersionFromLanguageVersion).sure {
    "Couldn't load KotlinClass from $outputFile; it may happen because class doesn't have valid Kotlin annotations"
  }
}