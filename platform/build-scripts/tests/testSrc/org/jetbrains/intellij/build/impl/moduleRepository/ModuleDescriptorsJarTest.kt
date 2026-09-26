// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.moduleRepository

import com.intellij.platform.runtime.repository.RuntimeModuleId
import com.intellij.platform.runtime.repository.RuntimeModuleId.DEFAULT_NAMESPACE
import com.intellij.platform.runtime.repository.RuntimeModuleLoadingRule
import com.intellij.platform.runtime.repository.impl.IncludedRuntimeModuleImpl
import com.intellij.platform.runtime.repository.impl.RuntimePluginHeaderImpl
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleDescriptor
import com.intellij.platform.runtime.repository.serialization.RuntimeModuleRepositorySerialization
import com.intellij.testFramework.rules.TempDirectoryExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile
import java.util.zip.ZipEntry

class ModuleDescriptorsJarTest {
  @JvmField
  @RegisterExtension
  val tempDirectory = TempDirectoryExtension()

  private val bootstrap = RuntimeModuleId.legacyJpsModule("intellij.platform.bootstrap")
  private val util = RuntimeModuleId.legacyJpsModule("intellij.platform.util")
  private val descriptors = listOf(
    RawRuntimeModuleDescriptor.create(bootstrap, listOf("../lib/bootstrap.jar"), listOf(util)),
    RawRuntimeModuleDescriptor.create(util, listOf("../lib/util.jar"), emptyList()),
  )
  private val pluginHeaders = listOf(
    RuntimePluginHeaderImpl(
      "com.foo",
      RuntimeModuleId.legacyJpsModule("foo.plugin"),
      listOf(IncludedRuntimeModuleImpl(RuntimeModuleId.contentModule("foo.core", DEFAULT_NAMESPACE), RuntimeModuleLoadingRule.OPTIONAL, null)),
    ),
  )

  @Test
  fun `same descriptors give the same bytes`() {
    val first = writeJar("first.jar")
    val second = writeJar("second.jar")
    assertThat(Files.readAllBytes(second)).isEqualTo(Files.readAllBytes(first))
  }

  @Test
  fun `jar loads back`() {
    val jar = writeJar("module-descriptors.jar")
    val data = RuntimeModuleRepositorySerialization.loadFromJar(jar)
    assertThat(data.allModuleIds).containsExactlyInAnyOrder(bootstrap, util)
    for (descriptor in descriptors) {
      assertThat(data.findDescriptor(descriptor.moduleId)).isEqualTo(descriptor)
    }
    assertThat(RuntimeModuleRepositorySerialization.loadBootstrapClasspath(jar, bootstrap.name))
      .containsExactly("../lib/bootstrap.jar", "../lib/util.jar")
  }

  @Test
  fun `entries are deflated and have no time`() {
    val entries = readEntries(Files.readAllBytes(writeJar("module-descriptors.jar")))
    assertThat(entries).hasSize(1 + descriptors.size + pluginHeaders.size)
    assertThat(entries.first().name).isEqualTo(JarFile.MANIFEST_NAME)
    assertThat(entries.map { it.method }).containsOnly(ZipEntry.DEFLATED)
    assertThat(entries.map { it.centralDosDateTime }).containsOnly(0)
    assertThat(entries.map { it.localDosDateTime }).containsOnly(0)
  }

  private fun writeJar(fileName: String): Path {
    val jar = tempDirectory.rootPath.resolve(fileName)
    writeModuleDescriptorsJar(descriptors, pluginHeaders, bootstrap.name, jar)
    return jar
  }
}

private data class RawZipEntry(
  @JvmField val name: String,
  @JvmField val method: Int,
  @JvmField val centralDosDateTime: Int,
  @JvmField val localDosDateTime: Int,
)

/**
 * Reads the entries from the raw bytes. [ZipEntry.getTimeLocal] cannot read a zero DOS date, because month 0 makes it throw.
 */
private fun readEntries(data: ByteArray): List<RawZipEntry> {
  val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
  // the archive has no comment, so the end of central directory record is the last 22 bytes
  val endOffset = data.size - 22
  assertThat(buffer.getInt(endOffset)).isEqualTo(0x06054b50)
  val entryCount = buffer.getShort(endOffset + 10).toInt() and 0xffff
  var offset = buffer.getInt(endOffset + 16)
  val result = ArrayList<RawZipEntry>(entryCount)
  repeat(entryCount) {
    assertThat(buffer.getInt(offset)).isEqualTo(0x02014b50)
    val nameLength = buffer.getShort(offset + 28).toInt() and 0xffff
    val extraLength = buffer.getShort(offset + 30).toInt() and 0xffff
    val commentLength = buffer.getShort(offset + 32).toInt() and 0xffff
    val localHeaderOffset = buffer.getInt(offset + 42)
    assertThat(buffer.getInt(localHeaderOffset)).isEqualTo(0x04034b50)
    result.add(RawZipEntry(
      name = String(data, offset + 46, nameLength, Charsets.UTF_8),
      method = buffer.getShort(offset + 10).toInt(),
      centralDosDateTime = buffer.getInt(offset + 12),
      localDosDateTime = buffer.getInt(localHeaderOffset + 10),
    ))
    offset += 46 + nameLength + extraLength + commentLength
  }
  return result
}
