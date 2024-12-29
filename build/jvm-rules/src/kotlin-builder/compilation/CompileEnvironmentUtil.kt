// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("PackageDirectoryMismatch")

package org.jetbrains.kotlin.cli.jvm.compiler

import org.jetbrains.bazel.jvm.kotlin.createJar
import org.jetbrains.intellij.build.io.ZipIndexWriter
import org.jetbrains.kotlin.backend.common.output.OutputFileCollection
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.modules.ModuleChunk
import org.jetbrains.kotlin.name.FqName
import java.io.File

class CompileEnvironmentUtil {
  companion object {
    @JvmStatic
    @Suppress("unused")
    fun loadModuleChunk(buildFile: File, messageCollector: MessageCollector): ModuleChunk {
      error("Module definition file is not supported and must not be used")
    }

    @JvmStatic
    @Suppress("unused")
    fun writeToJar(
      jarPath: File,
      @Suppress("unused") jarRuntime: Boolean,
      @Suppress("unused") noReflect: Boolean,
      @Suppress("unused") resetJarTimestamps: Boolean,
      mainClass: FqName?,
      outputFiles: OutputFileCollection,
      @Suppress("unused") messageCollector: MessageCollector
    ) {
      createJar(outputFiles = outputFiles, outFile = jarPath.toPath(), mainClass = mainClass, targetLabel = null)
    }
  }
}
