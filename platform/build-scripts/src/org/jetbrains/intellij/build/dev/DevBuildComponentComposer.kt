// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dev

import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes

@ApiStatus.Internal
fun mergeDevBuildComponent(source: Path, target: Path) {
  mergeDevBuildComponent(source = source, target = target) { destination, file ->
    Files.createLink(destination, file)
  }
}

internal fun mergeDevBuildComponent(
  source: Path,
  target: Path,
  linkFile: (destination: Path, source: Path) -> Unit,
) {
  Files.walkFileTree(source, object : SimpleFileVisitor<Path>() {
    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
      Files.createDirectories(target.resolve(source.relativize(dir).toString()))
      return FileVisitResult.CONTINUE
    }

    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
      val destination = target.resolve(source.relativize(file).toString())
      check(!Files.exists(destination)) { "Dev-build components both provide '${target.relativize(destination)}'" }
      if (Files.isSymbolicLink(file)) {
        Files.createSymbolicLink(destination, Files.readSymbolicLink(file))
      }
      else {
        try {
          linkFile(destination, file)
        }
        catch (_: IOException) {
          Files.copy(file, destination, StandardCopyOption.COPY_ATTRIBUTES)
        }
      }
      return FileVisitResult.CONTINUE
    }
  })
}
