// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.build.impl

import org.jetbrains.intellij.build.io.PackageIndexBuilder
import org.jetbrains.intellij.build.io.ZipFileWriter
import org.jetbrains.intellij.build.io.ZipIndexWriter
import org.jetbrains.intellij.build.io.writeNewZipWithoutIndex
import java.nio.ByteBuffer
import java.nio.channels.GatheringByteChannel
import java.nio.channels.SeekableByteChannel
import java.nio.file.Path
import java.util.*

internal fun buildKeymapPlugin(keymaps: Array<String>, buildNumber: String, targetDir: Path, keymapDir: Path): Pair<Path, ByteArray> {
  val longName = keymaps.first().replace("Mac OS X", "macOS").replace("Default for |[.0-9]", "").trim()
  val shortName = longName.replace(" ", "")

  val pluginXmlData = keymapPluginXml(
    version = buildNumber,
    id = shortName.lowercase(Locale.ENGLISH),
    name = longName,
    keymaps = keymaps,
  ).toByteArray()

  val buffer = ByteBuffer.allocate(128 * 1024)
  val packageIndexBuilder = PackageIndexBuilder()
  ZipFileWriter(
    channel = WritableByteChannelBackedByByteBuffer(buffer),
    zipIndexWriter = ZipIndexWriter(indexWriter = packageIndexBuilder.indexWriter),
  ).use { zipCreator ->
    zipCreator.uncompressedData("META-INF/plugin.xml", pluginXmlData)
    packageIndexBuilder.addFile("META-INF/plugin.xml")

    packageIndexBuilder.addFile("META-INF/pluginIcon.svg")
    @Suppress("SpellCheckingInspection")
    zipCreator.uncompressedData("META-INF/pluginIcon.svg",
                                      "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"40\" height=\"40\" viewBox=\"0 0 40 40\">\n" +
                                      "  <path fill=\"#389FD6\" fill-rule=\"evenodd\" d=\"M0,0 L36,0 L36,26 L0,26 L0,0 Z M16,5 L20,5 L20," +
                                      "9 L16,9 L16,5 Z M22,5 L26,5 L26,9 L22,9 L22,5 Z M28,5 L32,5 L32,9 L28,9 L28,5 Z M28,11 L32,11 L32," +
                                      "15 L28,15 L28,11 Z M22,11 L26,11 L26,15 L22,15 L22,11 Z M10,11 L14,11 L14,15 L10,15 L10,11 Z M4," +
                                      "11 L8,11 L8,15 L4,15 L4,11 Z M4,5 L8,5 L8,9 L4,9 L4,5 Z M10,5 L14,5 L14,9 L10,9 L10,5 Z M16,11 L20," +
                                      "11 L20,15 L16,15 L16,11 Z M25,21 L11,21 L11,17 L25,17 L25,21 Z\" transform=\"translate(2 7)\"/>\n" +
                                      "</svg>\n"
    )

    packageIndexBuilder.addFile("META-INF/pluginIcon_dark.svg")
    @Suppress("SpellCheckingInspection")
    zipCreator.uncompressedData("META-INF/pluginIcon_dark.svg",
                                      "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"40\" height=\"40\" viewBox=\"0 0 40 40\">\n" +
                                      "  <path fill=\"#3592C4\" fill-rule=\"evenodd\" d=\"M0,0 L36,0 L36,26 L0,26 L0,0 Z M16,5 L20,5 L20," +
                                      "9 L16,9 L16,5 Z M22,5 L26,5 L26,9 L22,9 L22,5 Z M28,5 L32,5 L32,9 L28,9 L28,5 Z M28,11 L32,11 L32," +
                                      "15 L28,15 L28,11 Z M22,11 L26,11 L26,15 L22,15 L22,11 Z M10,11 L14,11 L14,15 L10,15 L10,11 Z M4," +
                                      "11 L8,11 L8,15 L4,15 L4,11 Z M4,5 L8,5 L8,9 L4,9 L4,5 Z M10,5 L14,5 L14,9 L10,9 L10,5 Z M16,11 L20," +
                                      "11 L20,15 L16,15 L16,11 Z M25,21 L11,21 L11,17 L25,17 L25,21 Z\" transform=\"translate(2 7)\"/>\n" +
                                      "</svg>\n"
    )
    for (name in keymaps) {
      val keymapFile = "keymaps/$name.xml"
      packageIndexBuilder.addFile(keymapFile)
      zipCreator.file(keymapFile, keymapDir.resolve("$name.xml"))
    }

    packageIndexBuilder.writePackageIndex(zipCreator)
  }

  buffer.flip()

  val resultFile = targetDir.resolve("${shortName}Keymap.zip")
  writeNewZipWithoutIndex(resultFile, compress = true) {
    it.uncompressedData("${shortName}Keymap/lib/${shortName}Keymap.jar", buffer)
  }
  return Pair(resultFile, pluginXmlData)
}

private fun keymapPluginXml(version: String, id: String, name: String, keymaps: Array<String>): String {
  return """<idea-plugin>
<name>$name Keymap</name>
<id>com.intellij.plugins.${id}keymap</id>
<version>$version</version>
<idea-version since-build="${version.substring(0, version.lastIndexOf('.'))}"/>
<vendor>JetBrains</vendor>
<category>Keymap</category>
<description>
  $name keymap for all IntelliJ-based IDEs.
  Use this plugin if $name keymap is not pre-installed in your IDE.
</description>
<depends>com.intellij.modules.lang</depends>
<extensions defaultExtensionNs="com.intellij">
${
  keymaps.joinToString(separator = "\n") { "    <bundledKeymap file=\"$it.xml\"/>"  } 
}
</extensions>
</idea-plugin>"""
}

private class WritableByteChannelBackedByByteBuffer(private val buffer: ByteBuffer) : GatheringByteChannel, SeekableByteChannel {
  private var isOpen = true

  override fun isOpen() = isOpen

  override fun close() {
    isOpen = false
  }

  override fun read(dst: ByteBuffer): Int {
    throw UnsupportedOperationException()
  }

  override fun write(src: ByteBuffer): Int {
    val r = src.remaining()
    buffer.put(src)
    return r
  }

  override fun write(srcs: Array<ByteBuffer>, offset: Int, length: Int): Long {
    var bytesWritten: Long = 0
    for (i in offset until (offset + length)) {
      bytesWritten += write(srcs[i])
    }
    return bytesWritten
  }

  override fun write(srcs: Array<ByteBuffer>): Long = write(srcs = srcs, offset = 0, length = srcs.size)

  override fun position(): Long = buffer.position().toLong()

  @Synchronized
  override fun position(newPosition: Long): SeekableByteChannel {
    buffer.position(newPosition.toInt())
    return this
  }

  override fun size(): Long = buffer.limit().toLong()

  override fun truncate(size: Long): SeekableByteChannel {
    val limit = buffer.limit()
    if (limit > size) {
      buffer.limit(size.toInt())
    }
    return this
  }
}
