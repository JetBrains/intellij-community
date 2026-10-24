// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij

import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.ImageResourceLocation
import com.intellij.platform.icons.imageIconDescriptor
import com.intellij.platform.icons.impl.DefaultLayeredIconDescriptor
import com.intellij.platform.icons.impl.UrlImageResourceLocation
import com.intellij.platform.icons.impl.layers.ImageIconLayer
import com.intellij.platform.icons.swing.createSwingIcon
import com.intellij.testFramework.junit5.TestApplication
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir

/**
 * Images loaded by URL rather than from a class loader, `imageIconDescriptor(url)`: the icons the user chooses for an
 * action (a file on disk), and the default icons the customization dialog offers (a `jar:` URL into the icons jar).
 * Only local locations are accepted, so an icon never loads from the network. A file path is kept as it is, relative or
 * not, and resolved only when the image is loaded.
 */
@TestApplication
class UrlImageResourceTest {
  @TempDir
  lateinit var dir: Path

  @BeforeEach
  fun activateIconManager() {
    IntelliJIconManager.activate()
  }

  @Test
  fun `an absolute file path renders the file`() {
    val file = redSquare()

    val descriptor = imageIconDescriptor(file.toString())

    assertEquals(UrlImageResourceLocation(file.toString()), descriptor.location())
    assertEquals(16 * 16, countRed(render(descriptor)), "the whole red square must be rendered")
  }

  @Test
  fun `a relative file path is kept as it is and resolved against the working directory when loaded`() {
    // kept relative, so that it means the same on another machine, for example the frontend in remote development
    val relative = Path.of("").toAbsolutePath().relativize(redSquare()).toString()

    val descriptor = imageIconDescriptor(relative)

    assertEquals(UrlImageResourceLocation(relative), descriptor.location())
    assertEquals(16 * 16, countRed(render(descriptor)), "the whole red square must be rendered")
  }

  @Test
  fun `a file URL renders the file`() {
    val file = redSquare()

    assertEquals(16 * 16, countRed(render(imageIconDescriptor(file.toUri().toString()))))
  }

  @Test
  fun `a jar URL renders the image in the jar`() {
    val url = UrlImageResourceTest::class.java.classLoader.getResource("expui/general/save.svg")!!.toString()

    val image = render(imageIconDescriptor(url))

    assertTrue(countOpaque(image) > 0) { "the icon at $url must be rendered" }
  }

  @Test
  fun `network locations are rejected`() {
    for (url in listOf("https://example.com/icon.svg", "http://example.com/icon.svg", "ftp://example.com/icon.svg")) {
      assertThrows<IllegalArgumentException>("$url must be rejected") { imageIconDescriptor(url) }
    }
  }

  @Test
  fun `a Windows path with a drive letter is a file path, not a URL`() {
    assertEquals("C:/icons/icon.svg", UrlImageResourceLocation("C:/icons/icon.svg").url)
  }

  @Test
  fun `a location with a network URL cannot be created, also not by deserialization`() {
    // deserialization runs the constructor's validation as well
    assertThrows<IllegalArgumentException> { UrlImageResourceLocation("https://example.com/icon.svg") }
  }

  private fun redSquare(): Path {
    val file = dir.resolve("red.svg")
    Files.writeString(file, """<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16"><rect width="16" height="16" fill="#FF0000"/></svg>""")
    return file
  }

  private fun IconDescriptor.location(): ImageResourceLocation {
    val layer = (this as DefaultLayeredIconDescriptor).layers.single() as ImageIconLayer
    return layer.loader
  }

  private fun render(descriptor: IconDescriptor): BufferedImage {
    val swingIcon = descriptor.createSwingIcon()
    val image = BufferedImage(swingIcon.iconWidth, swingIcon.iconHeight, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    try {
      swingIcon.paintIcon(null, g, 0, 0)
    }
    finally {
      g.dispose()
    }
    return image
  }

  private fun countOpaque(image: BufferedImage): Int = pixels(image).count { it ushr 24 and 0xFF >= 128 }

  private fun countRed(image: BufferedImage): Int = pixels(image).count { it == 0xFFFF0000.toInt() }

  private fun pixels(image: BufferedImage): List<Int> =
    (0 until image.height).flatMap { y -> (0 until image.width).map { x -> image.getRGB(x, y) } }
}
