// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplaceNegatedIsEmptyWithIsNotEmpty")

package org.jetbrains.intellij.build.tasks

import com.intellij.diagnostic.telemetry.use
import io.opentelemetry.api.common.AttributeKey
import org.jetbrains.intellij.build.io.writeNewFile
import org.jetbrains.intellij.build.tracer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission

fun buildMacZip(targetFile: Path,
                zipRoot: String,
                productJson: ByteArray,
                allDist: Path,
                macDist: Path,
                extraFiles: Collection<Map.Entry<Path, String>>,
                executableFilePatterns: List<String>,
                compressionLevel: Int,
                errorsConsumer: (String) -> Unit) {
  tracer.spanBuilder("build zip archive for macOS")
    .setAttribute("file", targetFile.toString())
    .setAttribute("zipRoot", zipRoot)
    .setAttribute(AttributeKey.stringArrayKey("executableFilePatterns"), executableFilePatterns)
    .use {
      val fs = targetFile.fileSystem
      val patterns = executableFilePatterns.map { fs.getPathMatcher("glob:$it") }

      val entryCustomizer: EntryCustomizer = { entry, file, relativeFile ->
        when {
          patterns.any { it.matches(relativeFile) } -> entry.unixMode = executableFileUnixMode
          PosixFilePermission.OWNER_EXECUTE in Files.getPosixFilePermissions(file) -> {
            errorsConsumer("Executable permissions of $relativeFile won't be set in $targetFile. " +
                           "Please make sure that executable file patterns are updated.")
          }
        }
      }

      writeNewFile(targetFile) { targetFileChannel ->
        NoDuplicateZipArchiveOutputStream(targetFileChannel).use { zipOutStream ->
          zipOutStream.setLevel(compressionLevel)

          zipOutStream.entry("$zipRoot/Resources/product-info.json", productJson)

          val fileFilter: (Path, Path) -> Boolean = { sourceFile, relativeFile ->
            val path = relativeFile.toString()
            if (path.endsWith(".txt") && !path.contains('/')) {
              zipOutStream.entry("$zipRoot/Resources/$relativeFile", sourceFile)
              false
            }
            else {
              true
            }
          }

          zipOutStream.dir(allDist, "$zipRoot/", fileFilter = fileFilter, entryCustomizer = entryCustomizer)
          zipOutStream.dir(macDist, "$zipRoot/", fileFilter = fileFilter, entryCustomizer = entryCustomizer)

          for ((file, relativePath) in extraFiles) {
            zipOutStream.entry("$zipRoot/$relativePath${if (relativePath.isEmpty()) "" else "/"}${file.fileName}", file)
          }
        }
      }
    }
}