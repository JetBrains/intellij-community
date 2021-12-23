// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.testFramework.binaryReproducibility

import com.intellij.openapi.util.io.FileUtil
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.*
import java.util.jar.JarFile
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

  private fun assertTheSameContent(relativePath: Path, dir1: Path, dir2: Path): AssertionError? {
    val path1 = dir1.resolve(relativePath)
    val path2 = dir2.resolve(relativePath)
    if (!path1.isRegularFile() || path1.checksum() == path2.checksum()) {
      return null
    }
    val error = AssertionError("Failed for $relativePath")
    println(error.message)
    return if (relativePath.extension == "jar") {
      val jarContentComparisonError = assertTheSameContent(path1.unpackJar(), path2.unpackJar())
      if (jarContentComparisonError == null) {
        println("\tNo difference in $relativePath content, timestamp or ordering issue?")
        error
      }
      else {
        jarContentComparisonError
      }
    }
    else {
      saveDiff(relativePath, path1.content(), path2.content())
      error
    }
  }

  private fun saveDiff(relativePath: Path, content1: String, content2: String) {
    fun String.saveIn(subdir: String): Path {
      val textFileName = relativePath.name.removeSuffix(".txt") + ".txt"
      val target = diffDir.resolve(subdir)
        .resolve(relativePath)
        .resolveSibling(textFileName)
      target.parent.createDirectories()
      target.writeText(this@saveIn)
      return target
    }

    val a = content1.saveIn("a")
    val b = content2.saveIn("b")
    diff(a, b).saveIn("diff")
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

  private fun Path.content(): String =
    when (extension) {
      "jar" -> error("$this is expected to be already unpacked")
      "class" -> process("javap", "-verbose", "$this")
      "zip", "tar.gz", "gz", "tar", "exe" -> "$fileName"
      else -> try {
        Files.readString(this)
      }
      catch (io: IOException) {
        "unable to read text: ${io.message}"
      }
    }

  private fun Path.unpackJar(): Path {
    assert(extension == "jar")
    val name = "$fileName".replace(".", "_")
    val targetDir = tempDir
      .resolve("unpackedJars")
      .resolve(name)
      .resolve(UUID.randomUUID().toString())
    FileUtil.delete(targetDir)
    JarFile(toFile()).use { jar ->
      val meta = jar.entries().toList().associate { entry ->
        if (!entry.isDirectory) {
          jar.getInputStream(entry).use {
            val targetFile = targetDir.resolve(entry.name)
            targetFile.parent.createDirectories()
            // FIXME workaround for case-insensitive file systems
            if (!targetFile.exists()) targetFile.createFile()
            targetFile.writeBytes(it.readAllBytes(), StandardOpenOption.TRUNCATE_EXISTING)
          }
        }
        entry.name to entry.time
      }
      if (meta.isNotEmpty()) {
        val targetFile = targetDir.resolve("__${name}_archive_meta__")
        targetFile.parent.createDirectories()
        assert(!Files.exists(targetFile))
        targetFile.createFile()
        targetFile.writeText(meta.entries.joinToString(separator = System.lineSeparator()) { (path, date) ->
          "$path=$date"
        })
      }
    }
    return targetDir
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
    if (listingDiff.isNotEmpty()) {
      return AssertionError("Listing diff for $dir1 and $dir2: $listingDiff")
    }
    val contentComparisonFailures = relativeListing1.mapNotNull {
      assertTheSameContent(it, dir1, dir2)
    }
    return if (contentComparisonFailures.isNotEmpty()) {
      AssertionError("$dir1 doesn't match $dir2").apply {
        contentComparisonFailures.forEach(::addSuppressed)
      }
    }
    else null
  }
}
