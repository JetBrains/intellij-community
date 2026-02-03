// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.util.io.Compressor
import com.intellij.util.io.Decompressor
import com.intellij.util.io.PosixFilePermissionsUtil
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.attribute.PosixFilePermission
import java.util.concurrent.TimeUnit

fun tar(archive: Path, rootDir: String, directories: List<Path>, executableFileMatchers: Collection<PathMatcher>, buildDateInSeconds: Long) {
  val normalizedRootDir = rootDir.removeSuffix("/")
  FileSystemIndependentTarGzCompressor(archive) { entryName, fileSystemMode ->
    val relativePath = Path.of(entryName.removePrefix(rootDir).removePrefix("/"))
    if (executableFileMatchers.any { it.matches(relativePath) } ||
        PosixFilePermission.OWNER_EXECUTE in PosixFilePermissionsUtil.fromUnixMode(fileSystemMode)) {
      executableFileUnixMode
    }
    else 0
  }.use { compressor ->
    for (dir in directories) {
      require(Files.isDirectory(dir))
      compressor.addDirectory(normalizedRootDir, dir, buildDateInSeconds)
    }
  }
}

private class FileSystemIndependentTarGzCompressor(
  archive: Path,
  @JvmField val mode: (String, Int) -> Int,
) : Compressor.Tar(archive, Compression.GZIP) {
  override fun writeFileEntry(
    name: String,
    source: InputStream,
    length: Long,
    timestamp: Long,
    fileSystemMode: Int,
    symlinkTarget: String?,
  ) {
    super.writeFileEntry(name, source, length, timestamp, mode(name, fileSystemMode), symlinkTarget)
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
