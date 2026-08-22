// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.contentModule

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * The contract `jvm_library(content_module_jar = True)` relies on: the flag-file grammar, and the entry-level rules that make a
 * packed jar byte-identical to what the in-process `JarPackager` produces.
 *
 * Input jars are written with the JDK rather than with `//zip`, deliberately: the packer has to cope with any stored zip,
 * and a fixture built by the same writer under test would hide a reader bug.
 */
class ContentModulePackerTest {
  @get:Rule
  val tempFolder: TemporaryFolder = TemporaryFolder()

  @Test
  fun `a single module output is copied without the build-only entries`() {
    val module = storedJar(
      "module.jar",
      "a/B.class" to "class B",
      "icon-robots.txt" to "skip",
      "icons/expui/icon-robots.txt" to "skip",
      "module-info.class" to "skip",
      "classpath.index" to "skip",
      "messages/Bundle.properties" to "key=value",
    )
    val out = pack("module=$module")

    assertEquals(listOf("a/B.class", "messages/Bundle.properties", "__index__"), entryNames(out))
    assertEquals("class B", entryText(out, "a/B.class"))
  }

  @Test
  fun `no directory entries reach the output`() {
    val module = tempFolder.root.toPath().resolve("with-dirs.jar")
    ZipOutputStream(Files.newOutputStream(module)).use { stream ->
      stream.putNextEntry(ZipEntry("a/"))
      stream.closeEntry()
      writeStored(stream, "a/B.class", "class B")
    }

    assertEquals(listOf("a/B.class", "__index__"), entryNames(pack("module=$module")))
  }

  @Test
  fun `the first source wins, so a library beats a module output of the same name`() {
    val library = storedJar("lib.jar", "a/B.class" to "from library")
    val module = storedJar("module.jar", "a/B.class" to "from module")

    val log = StringWriter()
    val out = pack("library=$library", "module=$module", log = log)

    assertEquals("from library", entryText(out, "a/B.class"))
    assertTrue("expected the collision in the log, got: $log", log.toString().contains("a/B.class"))
  }

  @Test
  fun `keep-manifest decides whether the manifest survives`() {
    val library = storedJar("lib.jar", "META-INF/MANIFEST.MF" to "Manifest-Version: 1.0\n", "a/B.class" to "class B")

    assertFalse(entryNames(pack("library=$library")).contains("META-INF/MANIFEST.MF"))
    assertTrue(entryNames(pack("keep-manifest=true", "library=$library")).contains("META-INF/MANIFEST.MF"))
  }

  @Test
  fun `rewrite-boot-class-path points the manifest at the packed jar`() {
    val library = storedJar(
      "coverage.jar",
      "META-INF/MANIFEST.MF" to
        "Manifest-Version: 1.0\r\nPremain-Class: com.intellij.rt.coverage.main.CoveragePremain\r\n" +
        "Boot-Class-Path: intellij-coverage-agent-1.0.jar\r\nCan-Retransform-Classes: true\r\n\r\n",
      "a/B.class" to "class B",
    )

    val packed = pack("rewrite-boot-class-path=true", "library=$library")

    // The name of the jar it ends up in, which is what lets the agent instrument from any class loader.
    assertTrue(entryNames(packed).contains("META-INF/MANIFEST.MF"))
    assertTrue(entryText(packed, "META-INF/MANIFEST.MF").contains("Boot-Class-Path: ${packed.fileName}\r\n"))
    assertFalse(entryText(packed, "META-INF/MANIFEST.MF").contains("intellij-coverage-agent"))

    // ...and deliberately absent from `__index__`: `mergeJars.kt` writes this entry ahead of its own indexing, so a
    // packed jar that indexed it would no longer be byte-identical to the one the distribution ships. A jar that only
    // *keeps* its manifest does index it, which is what makes the two indexes differ.
    val kept = pack("keep-manifest=true", "library=$library")
    assertEquals(entryNames(kept), entryNames(packed))
    assertTrue(entrySize(kept, "__index__") > entrySize(packed, "__index__"))
  }

  @Test
  fun `library entries that would collide or ship for nothing are dropped`() {
    val library = storedJar(
      "lib.jar",
      "a/B.class" to "class B",
      "META-INF/LICENSE" to "skip",
      "META-INF/NOTICE.txt" to "skip",
      "META-INF/INDEX.LIST" to "skip",
      "META-INF/versions/9/module-info.class" to "skip",
      "META-INF/FOO.SF" to "skip",
      "native/libfoo.so" to "skip",
      "licenses/third-party.txt" to "skip",
    )

    assertEquals(listOf("a/B.class", "__index__"), entryNames(pack("library=$library")))
  }

  @Test
  fun `a module output keeps what a library would lose`() {
    // The two filters are deliberately different: `META-INF/NOTICE` in our own module output is our own resource.
    val module = storedJar("module.jar", "META-INF/NOTICE" to "ours", "a/B.class" to "class B")

    assertEquals(listOf("META-INF/NOTICE", "a/B.class", "__index__"), entryNames(pack("module=$module")))
  }

  @Test
  fun `one flag file packs several jars`() {
    val first = storedJar("first-module.jar", "a/B.class" to "class B")
    val second = storedJar("second-module.jar", "c/D.class" to "class D")

    val outputs = tempFolder.root.toPath()
    val exitCode = runPacker(
      "output=${outputs.resolve("first.jar")}",
      "module=$first",
      "output=${outputs.resolve("second.jar")}",
      "module=$second",
    )

    assertEquals(0, exitCode)
    assertEquals(listOf("a/B.class", "__index__"), entryNames(outputs.resolve("first.jar")))
    assertEquals(listOf("c/D.class", "__index__"), entryNames(outputs.resolve("second.jar")))
  }

  @Test
  fun `entries are copied byte for byte`() {
    val payload = ByteArray(64 * 1024) { (it % 251).toByte() }
    val module = tempFolder.root.toPath().resolve("big.jar")
    ZipOutputStream(Files.newOutputStream(module)).use { writeStored(it, "a/Big.class", payload) }

    val out = pack("module=$module")
    ZipFile(out.toFile()).use { zip ->
      val entry = zip.getEntry("a/Big.class")
      assertEquals(payload.size.toLong(), entry.size)
      assertTrue(payload.contentEquals(zip.getInputStream(entry).readBytes()))
    }
  }

  @Test
  fun `a malformed flag file fails instead of writing half a jar`() {
    val module = storedJar("module.jar", "a/B.class" to "class B")
    val output = tempFolder.root.toPath().resolve("out.jar")

    assertEquals(3, runPacker("output=$output", "nonsense=1"))
    assertEquals(3, runPacker("module=$module"))
    assertEquals(3, runPacker("output=$output", "module=$module", "output=$output", "module=$module"))
    assertEquals(3, runPacker("output=$output"))
  }

  private fun pack(vararg groupLines: String, log: StringWriter = StringWriter()): Path {
    val output = tempFolder.root.toPath().resolve("packed-${groupLines.contentHashCode()}.jar")
    val exitCode = runPacker(lines = arrayOf("output=$output") + groupLines, log = log)
    assertEquals("packing failed: $log", 0, exitCode)
    return output
  }

  private fun runPacker(vararg lines: String, log: StringWriter = StringWriter()): Int {
    val flagFile = Files.createTempFile(tempFolder.root.toPath(), "flags", ".txt")
    Files.write(flagFile, lines.toList())
    return runBlocking {
      packAll(arguments = listOf("--flagfile=$flagFile"), writer = log, baseDir = tempFolder.root.toPath())
    }
  }

  private fun storedJar(name: String, vararg entries: Pair<String, String>): Path {
    val file = tempFolder.root.toPath().resolve(name)
    ZipOutputStream(Files.newOutputStream(file)).use { stream ->
      for ((entryName, content) in entries) {
        writeStored(stream, entryName, content)
      }
    }
    return file
  }

  private fun writeStored(stream: ZipOutputStream, name: String, content: String) {
    writeStored(stream, name, content.toByteArray())
  }

  private fun writeStored(stream: ZipOutputStream, name: String, content: ByteArray) {
    val entry = ZipEntry(name)
    entry.method = ZipEntry.STORED
    entry.size = content.size.toLong()
    entry.compressedSize = content.size.toLong()
    entry.crc = CRC32().let {
      it.update(content)
      it.value
    }
    stream.putNextEntry(entry)
    stream.write(content)
    stream.closeEntry()
  }

  private fun entrySize(jar: Path, name: String): Long {
    return ZipFile(jar.toFile()).use { zip -> zip.getEntry(name).size }
  }

  private fun entryNames(jar: Path): List<String> {
    return ZipFile(jar.toFile()).use { zip -> zip.entries().asSequence().map { it.name }.toList() }
  }

  private fun entryText(jar: Path, name: String): String {
    return ZipFile(jar.toFile()).use { zip -> zip.getInputStream(zip.getEntry(name)).readBytes().decodeToString() }
  }
}
