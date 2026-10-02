// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.jps.model.module.JpsModule
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal class ModuleOutputEntryIndexTest {
  private val descriptor = "META-INF/feature.xml"

  @Test
  fun `the first holder in project order answers`() {
    val modules = modules("intellij.a", "intellij.b", "intellij.c")
    val index = ModuleOutputEntryIndex.build(modules) { module ->
      if (module.name == "intellij.a") listOf("META-INF/other.xml") else listOf(descriptor)
    }
    val reads = ArrayList<String>()

    val data = index.find(relativePath = descriptor, moduleNamePrefix = null, processedModules = null) { module ->
      reads.add(module.name)
      module.name.encodeToByteArray()
    }

    assertThat(data).isEqualTo("intellij.b".encodeToByteArray())
    assertThat(reads).containsExactly("intellij.b")
  }

  @Test
  fun `the prefix filter skips a holder`() {
    val modules = modules("intellij.a", "fleet.b", "intellij.c")
    val index = ModuleOutputEntryIndex.build(modules) { listOf(descriptor) }

    val data = index.find(relativePath = descriptor, moduleNamePrefix = "intellij.", processedModules = null) { module ->
      module.name.encodeToByteArray()
    }

    assertThat(data).isEqualTo("intellij.a".encodeToByteArray())
  }

  @Test
  fun `a processed module is skipped and every candidate becomes processed`() {
    val modules = modules("intellij.a", "intellij.b", "intellij.c", "fleet.d")
    val index = ModuleOutputEntryIndex.build(modules) { module ->
      if (module.name == "intellij.c") emptyList() else listOf(descriptor)
    }
    val processedModules = HashSet<String>()
    processedModules.add("intellij.a")

    val data = index.find(relativePath = descriptor, moduleNamePrefix = "intellij.", processedModules = processedModules) { module ->
      module.name.encodeToByteArray()
    }

    assertThat(data).isEqualTo("intellij.b".encodeToByteArray())
    // the scan adds every candidate before it reads, so a candidate after the hit is processed too
    assertThat(processedModules).containsExactlyInAnyOrder("intellij.a", "intellij.b", "intellij.c")
  }

  @Test
  fun `a miss reads nothing`() {
    val modules = modules("intellij.a", "intellij.b")
    val index = ModuleOutputEntryIndex.build(modules) { listOf("META-INF/other.xml") }
    val processedModules = HashSet<String>()

    val data = index.find(relativePath = descriptor, moduleNamePrefix = null, processedModules = processedModules) {
      error("no read is expected")
    }

    assertThat(data).isNull()
    assertThat(processedModules).containsExactlyInAnyOrder("intellij.a", "intellij.b")
  }

  @Test
  fun `an unlisted module is read in its place`() {
    val modules = modules("intellij.a", "intellij.b", "intellij.c")
    val index = ModuleOutputEntryIndex.build(modules) { module ->
      when (module.name) {
        "intellij.a" -> null
        "intellij.b" -> throw IllegalStateException("cannot list")
        else -> listOf(descriptor)
      }
    }
    val reads = ArrayList<String>()

    val data = index.find(relativePath = descriptor, moduleNamePrefix = null, processedModules = null) { module ->
      reads.add(module.name)
      if (module.name == "intellij.c") module.name.encodeToByteArray() else null
    }

    assertThat(data).isEqualTo("intellij.c".encodeToByteArray())
    assertThat(reads).containsExactly("intellij.a", "intellij.b", "intellij.c")
  }

  @Test
  fun `the first failure before the hit is the answer`() {
    val modules = modules("intellij.a", "intellij.b")
    val index = ModuleOutputEntryIndex.build(modules) { module -> if (module.name == "intellij.a") null else listOf(descriptor) }

    assertThatThrownBy {
      index.find(relativePath = descriptor, moduleNamePrefix = null, processedModules = null) { module ->
        if (module.name == "intellij.a") throw IllegalStateException("broken output") else module.name.encodeToByteArray()
      }
    }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessage("broken output")
  }

  @Test
  fun `only a relative xml path is indexed`() {
    assertThat(ModuleOutputEntryIndex.isIndexedName("META-INF/plugin.xml")).isTrue()
    assertThat(ModuleOutputEntryIndex.isIndexedName("/META-INF/plugin.xml")).isFalse()
    assertThat(ModuleOutputEntryIndex.isIndexedName("META-INF/MANIFEST.MF")).isFalse()
  }

  @Test
  fun `the pool lists the entry names of a jar`(@TempDir tempDir: Path) {
    val jar = tempDir.resolve("module.jar")
    ZipOutputStream(Files.newOutputStream(jar)).use { out ->
      for (name in listOf("META-INF/", "META-INF/plugin.xml", "META-INF/MANIFEST.MF", "a/B.class")) {
        out.putNextEntry(ZipEntry(name))
        out.closeEntry()
      }
    }
    val pool = ModuleOutputZipFilePool(lifetime = null)

    assertThat(pool.readEntryNames(jar, ModuleOutputEntryIndex::isIndexedName)).containsExactly("META-INF/plugin.xml")
    assertThat(pool.readEntryNames(tempDir.resolve("missing.jar"), ModuleOutputEntryIndex::isIndexedName)).isEmpty()
  }

  private fun modules(vararg names: String): List<JpsModule> {
    return names.map { name ->
      val module = mock(JpsModule::class.java)
      `when`(module.name).thenReturn(name)
      module
    }
  }
}
