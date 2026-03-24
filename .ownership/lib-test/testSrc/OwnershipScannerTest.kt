package com.intellij.codeowners

import com.intellij.codeowners.serialization.OwnershipScanner
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class OwnershipScannerTest {

  @TempDir
  lateinit var tempDir: Path

  @Test
  fun `finds OWNERSHIP files when no ignore rules`() {
    val rootOwnership = writeFile(tempDir.resolve("OWNERSHIP"), "root")
    val nestedOwnership = writeFile(tempDir.resolve("a/b/OWNERSHIP"), "nested")

    val found = OwnershipScanner.doScan(tempDir).toSet()

      Assertions.assertEquals(setOf(rootOwnership, nestedOwnership), found)
  }

  @Test
  fun `skips subtree when directory is ignored`() {
    // Ignore the whole subtree "ignored/"
    writeIgnore(tempDir, """
      ignored/
    """.trimIndent())

    val visibleOwnership = writeFile(tempDir.resolve("ok/OWNERSHIP"), "ok")
    writeFile(tempDir.resolve("ignored/OWNERSHIP"), "should not be found")
    writeFile(tempDir.resolve("ignored/deep/OWNERSHIP"), "should not be found")

    val found = OwnershipScanner.doScan(tempDir).toSet()

      Assertions.assertEquals(setOf(visibleOwnership), found)
  }

  @Test
  fun `supports negation in parent ignore file (unignore beats ignore)`() {
    writeIgnore(tempDir, """
      subdir/*
      !subdir/keep/
    """.trimIndent())

    writeFile(tempDir.resolve("subdir/skip/OWNERSHIP"), "should not be found")
    val keptOwnership = writeFile(tempDir.resolve("subdir/keep/OWNERSHIP"), "should be found")

    val found = OwnershipScanner.doScan(tempDir).toSet()

      Assertions.assertTrue(keptOwnership in found, "Expected keep/OWNERSHIP to be found due to negation rule")
      Assertions.assertFalse(found.any { it.toString().contains("${sep()}subdir${sep()}skip${sep()}OWNERSHIP") })
  }

  @Test
  fun `child ignore overrides parent decision (deeper context wins)`() {
    writeIgnore(tempDir, """
      !subdir/OWNERSHIP
    """.trimIndent())

    writeIgnore(tempDir.resolve("subdir"), """
      OWNERSHIP
    """.trimIndent())

    val shouldBeIgnored = writeFile(tempDir.resolve("subdir/OWNERSHIP"), "content")

    val found = OwnershipScanner.doScan(tempDir).toSet()

      Assertions.assertFalse(shouldBeIgnored in found, "Expected child ignore to override parent unignore")
  }

  @Test
  fun `root ignore rules apply when scanning filesystem root path`() {
    val archivePath = tempDir.resolve("scanner-root.zip")
    val archiveUri = URI.create("jar:${archivePath.toUri()}")

    FileSystems.newFileSystem(archiveUri, mapOf("create" to "true")).use { zipFs ->
      val root = zipFs.getPath("/")
      writeIgnore(root, """
        ignored/
      """.trimIndent())

      val visibleOwnership = writeFile(root.resolve("ok/OWNERSHIP"), "ok")
      writeFile(root.resolve("ignored/OWNERSHIP"), "ignored")

      val found = OwnershipScanner.doScan(root).toSet()

      Assertions.assertEquals(setOf(visibleOwnership), found)
    }
  }

  private fun writeIgnore(dir: Path, content: String) {
    dir.createDirectories()
    writeFile(dir.resolve(".ownership.scan.ignore"), content)
  }

  private fun writeFile(path: Path, content: String): Path {
    path.parent?.createDirectories()
    path.writeText(content)
    return path.normalize()
  }

  private fun sep(): String = File.separator
}
