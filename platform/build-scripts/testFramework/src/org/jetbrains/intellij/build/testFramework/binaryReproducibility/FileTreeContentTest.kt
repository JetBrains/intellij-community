// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.testFramework.binaryReproducibility

import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.Decompressor
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.*
import kotlin.io.path.*
import kotlin.streams.toList

class FileTreeContentTest(private val diffDir: Path = Path.of(System.getProperty("user.dir")).resolve(".diff"),
                          private val tempDir: Path = Files.createTempDirectory(this::class.java.simpleName)) {
  companion object {
    @JvmStatic
    fun main(args: Array<String>) {
      require(args.count() == 2)
      val assertion = FileTreeContentTest().assertTheSameContent(Path.of(args[0]), Path.of(args[1]))
      if (assertion != null) throw assertion
    }
  }

  init {
    FileUtil.delete(diffDir)
    Files.createDirectories(diffDir)
  }

  private fun listingDiff(firstIteration: Set<Path>, nextIteration: Set<Path>) =
    ((firstIteration - nextIteration) + (nextIteration - firstIteration))
      .filterNot { it.name == ".DS_Store" }

  fun assertTheSame(relativeFilePath: Path, dir1: Path, dir2: Path): AssertionError? {
    val path1 = dir1.resolve(relativeFilePath)
    val path2 = dir2.resolve(relativeFilePath)
    if (!Files.exists(path1) ||
        !Files.exists(path2) ||
        !path1.isRegularFile() ||
        path1.checksum() == path2.checksum() &&
        path1.permissions() == path2.permissions()) {
      return null
    }
    println("Failed for $relativeFilePath")
    val contentError = when (relativeFilePath.extension) {
      "tar.gz", "gz", "tar" -> assertTheSameContent(
        path1.unpackingDir().also { Decompressor.Tar(path1).extract(it) },
        path2.unpackingDir().also { Decompressor.Tar(path2).extract(it) }
      ) ?: AssertionError("No difference in $relativeFilePath content. Timestamp or ordering issue?")
      "zip", "jar", "ijx" -> assertTheSameContent(
        path1.unpackingDir().also { Decompressor.Zip(path1).extract(it) },
        path2.unpackingDir().also { Decompressor.Zip(path2).extract(it) }
      ) ?: AssertionError("No difference in $relativeFilePath content. Timestamp or ordering issue?")
      else -> if (path1.checksum() != path2.checksum()) {
        saveDiff(relativeFilePath, path1, path2)
        AssertionError("Checksum mismatch for $relativeFilePath")
      }
      else null
    }
    if (path1.permissions() != path2.permissions()) {
      val permError = AssertionError("Permissions mismatch for $relativeFilePath: ${path1.permissions()} vs ${path2.permissions()}")
      contentError?.addSuppressed(permError) ?: return permError
    }
    requireNotNull(contentError)
    return contentError
  }

  private fun saveDiff(relativePath: Path, file1: Path, file2: Path) {
    fun fileIn(subdir: String): Path {
      val textFileName = relativePath.name.removeSuffix(".txt") + ".txt"
      val target = diffDir.resolve(subdir)
        .resolve(relativePath)
        .resolveSibling(textFileName)
      target.parent.createDirectories()
      return target
    }
    val a = fileIn("a")
    val b = fileIn("b")
    file1.writeContent(a)
    file2.writeContent(b)
    fileIn("diff").writeText(diff(a, b))
  }

  private fun Path.checksum(): String = inputStream().buffered().use { input ->
    val digest = MessageDigest.getInstance("SHA-256")
    DigestInputStream(input, digest).use {
      var bytesRead = 0
      val buffer = ByteArray(1024 * 8)
      while (bytesRead != -1) {
        bytesRead = it.read(buffer)
      }
    }
    Base64.getEncoder().encodeToString(digest.digest())
  }

  private fun Path.permissions(): Set<PosixFilePermission> =
    Files.getFileAttributeView(this, PosixFileAttributeView::class.java)
      ?.readAttributes()?.permissions() ?: emptySet()

  private fun Path.writeContent(target: Path) {
    when (extension) {
      "jar", "zip", "tar.gz", "gz", "tar", "ijx" -> error("$this is expected to be already unpacked")
      "class" -> target.writeText(process("javap", "-verbose", "$this"))
      else -> copyTo(target, overwrite = true)
    }
  }

  private fun Path.unpackingDir(): Path {
    val unpackingDir = tempDir
      .resolve("unpacked")
      .resolve("$fileName".replace(".", "_"))
      .resolve(UUID.randomUUID().toString())
    FileUtil.delete(unpackingDir)
    return unpackingDir
  }

  private fun diff(path1: Path, path2: Path) = process("git", "diff", "--no-index", "--", "$path1", "$path2")

  private fun process(vararg command: String): String {
    val process = ProcessBuilder(*command).start()
    val output = process.inputStream.bufferedReader().use { it.readText() }
    process.waitFor()
    return output
  }

  fun assertTheSameContent(dir1: Path, dir2: Path): AssertionError? {
    val listing1 = Files.walk(dir1).use { it.toList() }
    val listing2 = Files.walk(dir2).use { it.toList() }
    val relativeListing1 = listing1.map(dir1::relativize)
    val listingDiff = listingDiff(relativeListing1.toSet(), listing2.map(dir2::relativize).toSet())
    val contentComparisonFailures = relativeListing1.mapNotNull { assertTheSame(it, dir1, dir2) }
    return when {
      listingDiff.isNotEmpty() -> AssertionError(listingDiff.joinToString(prefix = "Listing diff for $dir1 and $dir2:\n", separator = "\n"))
      contentComparisonFailures.isNotEmpty() -> AssertionError("$dir1 doesn't match $dir2")
      else -> null
    }?.apply {
      contentComparisonFailures.forEach(::addSuppressed)
    }
  }
}
