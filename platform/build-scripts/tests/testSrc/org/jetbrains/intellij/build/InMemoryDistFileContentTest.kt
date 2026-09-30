// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

class InMemoryDistFileContentTest {
  @Test
  fun `segments are concatenated in order`() {
    val content = InMemoryDistFileContent.ofSegments(listOf(bytes("ab"), bytes("cd"), bytes("e")))

    assertThat(content.readAllBytes()).isEqualTo(bytes("abcde"))
    assertThat(content.readAsStringForDebug()).isEqualTo("abcde")
  }

  @Test
  fun `one segment and several segments with equal bytes are equal and hash alike`() {
    val single = InMemoryDistFileContent(bytes("abcde"))
    val segmented = InMemoryDistFileContent.ofSegments(listOf(bytes("a"), bytes("bcd"), bytes("e")))
    val otherSegmented = InMemoryDistFileContent.ofSegments(listOf(bytes("abc"), bytes("de")))

    assertThat(segmented).isEqualTo(single)
    assertThat(single).isEqualTo(segmented)
    assertThat(segmented).isEqualTo(otherSegmented)
    assertThat(segmented.hashCode()).isEqualTo(single.hashCode())
    assertThat(otherSegmented.hashCode()).isEqualTo(single.hashCode())
    assertThat(single.hashCode()).isEqualTo(bytes("abcde").contentHashCode())
  }

  @Test
  fun `contents with different bytes are not equal`() {
    val content = InMemoryDistFileContent.ofSegments(listOf(bytes("ab"), bytes("cd")))

    assertThat(content).isNotEqualTo(InMemoryDistFileContent(bytes("abce")))
    assertThat(content).isNotEqualTo(InMemoryDistFileContent(bytes("abc")))
  }

  @Test
  fun `writeTo writes the same bytes as readAllBytes`() {
    val content = InMemoryDistFileContent.ofSegments(listOf(bytes("prefix"), bytes("-"), bytes("tail")))
    val out = ByteArrayOutputStream()

    content.writeTo(out)

    assertThat(out.toByteArray()).isEqualTo(content.readAllBytes())
  }

  @Test
  fun `empty segments are dropped and the size is the sum`() {
    val content = InMemoryDistFileContent.ofSegments(listOf(ByteArray(0), bytes("abc"), ByteArray(0), bytes("de")))

    assertThat(content.size).isEqualTo(5)
    assertThat(content).isEqualTo(InMemoryDistFileContent(bytes("abcde")))
    assertThat(InMemoryDistFileContent.ofSegments(listOf(ByteArray(0))).readAllBytes()).isEmpty()
  }

  @Test
  fun `one segment is returned without a copy`() {
    val data = bytes("abc")

    assertThat(InMemoryDistFileContent(data).readAllBytes()).isSameAs(data)
    assertThat(InMemoryDistFileContent.ofSegments(listOf(ByteArray(0), data)).readAllBytes()).isSameAs(data)
  }

  @Test
  fun `the debug string stops after 1024 bytes across segments`() {
    val content = InMemoryDistFileContent.ofSegments(listOf(ByteArray(1000) { 'a'.code.toByte() }, ByteArray(100) { 'b'.code.toByte() }))

    assertThat(content.readAsStringForDebug()).isEqualTo("a".repeat(1000) + "b".repeat(24))
  }

  private fun bytes(value: String): ByteArray = value.encodeToByteArray()
}
