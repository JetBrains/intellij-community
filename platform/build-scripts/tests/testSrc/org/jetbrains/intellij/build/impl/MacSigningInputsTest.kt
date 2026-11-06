// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.BuildPaths.Companion.COMMUNITY_ROOT
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

class MacSigningInputsTest {
  @Test
  fun `the content of a file decides a Mach-O binary, not its name or its mode`(@TempDir root: Path) {
    val tool = root.resolve("bin/tool").also { it.parent.createDirectories(); it.writeBytes(machOHeader()) }
    val library = root.resolve("lib/native.dylib").also { it.parent.createDirectories(); it.writeBytes(machOHeader()) }
    val script = root.resolve("bin/run.sh").also { it.writeText("#!/bin/sh\necho run\n") }
    val archive = root.resolve("lib/app.jar").also { it.writeBytes(byteArrayOf(0x50, 0x4b, 0x03, 0x04)) }
    root.resolve("bin/short").writeBytes(byteArrayOf(0x01))
    setMode(tool, "rw-r--r--")
    setMode(library, "rw-r--r--")
    setMode(script, "rwxr-xr-x")

    val inputs = collectMacSigningInputs(root)

    assertThat(inputs.binaries).containsExactlyInAnyOrder(tool, library)
    assertThat(inputs.archives).containsExactly(archive)
  }

  /**
   * The dev dist collector reads the same vectors with its own reader (`filemeta::macho` of the dev dist tools), so the
   * two readers keep one rule.
   */
  @Test
  fun `each Mach-O vector of the dev dist tools is a binary exactly when it has a kind`(@TempDir root: Path) {
    val vectors = Json.parseToJsonElement(Files.readString(MAC_BINARY_VECTORS)).jsonObject.getValue("vectors").jsonArray
    assertThat(vectors).hasSize(14)
    for (vector in vectors.map { it.jsonObject }) {
      val name = vector.getValue("name").jsonPrimitive.content
      val file = root.resolve("file")
      file.writeBytes(vector.getValue("bytes").jsonPrimitive.content.hexToByteArray())
      val binary = vector.getValue("kind") !is JsonNull
      assertThat(isMacBinary(file)).describedAs(name).isEqualTo(binary)
    }
  }
}

private val MAC_BINARY_VECTORS: Path =
  COMMUNITY_ROOT.communityRoot.resolve("build/dev-dist-tools/crates/filemeta/testdata/mac-binary-vectors.json")

/** The header of a thin 64-bit arm64 executable with no load commands. */
private fun machOHeader(): ByteArray {
  val buffer = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
  buffer.putInt(0xFEEDFACF.toInt()) // MH_MAGIC_64
  buffer.putInt(0x0100000C) // CPU_TYPE_ARM64
  buffer.putInt(0) // CPU_SUBTYPE_ARM64_ALL
  buffer.putInt(2) // MH_EXECUTE
  buffer.putInt(0) // ncmds
  buffer.putInt(0) // sizeofcmds
  buffer.putInt(0) // flags
  buffer.putInt(0) // reserved
  return buffer.array()
}

private fun setMode(file: Path, mode: String) {
  if (Files.getFileStore(file).supportsFileAttributeView("posix")) {
    Files.setPosixFilePermissions(file, PosixFilePermissions.fromString(mode))
  }
}
