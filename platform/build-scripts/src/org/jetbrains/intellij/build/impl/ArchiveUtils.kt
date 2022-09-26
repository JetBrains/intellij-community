// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.util.io.Compressor
import com.intellij.util.io.Decompressor

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

object ArchiveUtils {
  fun tar(archive: Path, rootDir: String, paths: List<String>, buildDateInSeconds: Long) {
    val normalizedRootDir = rootDir.removeSuffix("/")
    Compressor.Tar(archive.toFile(), Compressor.Tar.Compression.GZIP).use { compressor ->
      for (it in paths) {
        val path = Path.of(it)
        if (Files.isDirectory(path)) {
          compressor.addDirectory(normalizedRootDir, path, buildDateInSeconds)
        }
        else {
          compressor.addFile("$normalizedRootDir/${path.fileName}", path, buildDateInSeconds)
        }
      }
    }
  }

  fun unTar(archive: Path, destination: Path, rootDirToBeStripped: String? = null) {
    if (SystemInfoRt.isWindows) {
      val decompressor = Decompressor.Tar(archive)
      if (rootDirToBeStripped != null) {
        decompressor.removePrefixPath(rootDirToBeStripped)
      }
      decompressor.extract(destination)
    }
    else {
      // 'tar' command is used for performance reasons
      // both GNU Tar and BSD Tar will suffice
      Files.createDirectories(destination)
      val args = ArrayList<String>()
      args.add("tar")
      args.add("--extract")
      args.add("--gzip")
      args.add("--file=${archive.fileName}")
      if (rootDirToBeStripped != null) {
        args.add("--strip")
        args.add("1")
      }
      args.add("--directory")
      args.add(destination.toString())
      callProcess(args, archive.parent)
    }
  }

  private fun callProcess(args: List<String>, workDir: Path) {
    val builder = ProcessBuilder(args)
      .directory(workDir.toFile())
      .inheritIO()
    val process = builder.start()
    if (!process.waitFor(10, TimeUnit.MINUTES)) {
      process.destroyForcibly().waitFor()
      throw IllegalStateException("Cannot execute $args: 10 min timeout")
    }

    val exitCode = process.exitValue()
    check(exitCode == 0) {
      "Cannot execute $args (exitCode=$exitCode)"
    }
  }
}
