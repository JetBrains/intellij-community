// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij

import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.IconManager
import com.intellij.platform.icons.ImageResourceLocation
import com.intellij.platform.icons.iconDescriptor
import com.intellij.platform.icons.imageFileIconDescriptor
import com.intellij.platform.icons.imageUrlIconDescriptor
import com.intellij.platform.icons.impl.DefaultIconManager
import com.intellij.platform.icons.impl.DefaultLayeredIconDescriptor
import com.intellij.platform.icons.impl.FileImageResourceLocation
import com.intellij.platform.icons.impl.UrlImageResourceLocation
import com.intellij.platform.icons.impl.layers.ImageIconLayer
import com.intellij.platform.icons.swing.createSwingIcon
import com.intellij.testFramework.junit5.TestApplication
import java.awt.image.BufferedImage
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.io.TempDir
import kotlin.io.path.invariantSeparatorsPathString

/**
 * Images loaded from a file or a URL rather than from a class loader, `imageFileIconDescriptor` and
 * `imageUrlIconDescriptor`: the icons the user chooses for an action (a file on disk), and the default icons the
 * customization dialog offers (a `jar:` URL into the icons jar). Only local locations are accepted, so an icon never
 * loads from the network.
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
  fun `an image file renders the file`() {
    val file = redSquare()

    val descriptor = imageFileIconDescriptor(file)

    assertEquals(FileImageResourceLocation(file.invariantSeparatorsPathString), descriptor.location())
    assertEquals(16 * 16, countRed(render(descriptor)), "the whole red square must be rendered")
  }

  @Test
  fun `a relative image file is kept relative and resolved against the working directory when loaded`() {
    // kept relative, so that it means the same on another machine, for example the frontend in remote development
    val workingDirectory = Path.of("").toAbsolutePath()
    val file = redSquare()
    // on Windows, no relative path leads to another drive
    assumeTrue(file.root == workingDirectory.root, "the temporary directory and the working directory have different roots")
    val relative = workingDirectory.relativize(file)

    val descriptor = imageFileIconDescriptor(relative)

    assertEquals(FileImageResourceLocation(relative.invariantSeparatorsPathString), descriptor.location())
    assertEquals(16 * 16, countRed(render(descriptor)), "the whole red square must be rendered")
  }

  @Test
  fun `a file location keeps its relative path through serialization`() {
    // e.g. sent to the frontend in remote development
    val json = Json { serializersModule = (IconManager.getInstance() as DefaultIconManager).getSerializersModule() }
    val location: ImageResourceLocation = FileImageResourceLocation("icons/custom.svg")

    val text = json.encodeToString(PolymorphicSerializer(ImageResourceLocation::class), location)

    assertEquals(location, json.decodeFromString(PolymorphicSerializer(ImageResourceLocation::class), text))
  }

  @Test
  fun `a file location keeps the path with slash separators`() {
    val descriptor = imageFileIconDescriptor(Path.of("icons", "custom.svg"))

    assertEquals(FileImageResourceLocation("icons/custom.svg"), descriptor.location())
  }

  @Test
  fun `a file location with an invalid path has no URL and an empty image`() {
    // a NUL character is not valid in a path on any OS
    val location = FileImageResourceLocation("icons/in\u0000valid.svg")

    assertEquals(null, location.toUrl())
    val swingIcon = iconDescriptor { image(location) }.createSwingIcon()
    assertEquals(0, swingIcon.iconWidth, "an image that is not drawn has no size")
    assertEquals(0, swingIcon.iconHeight, "an image that is not drawn has no size")
    val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    try {
      assertDoesNotThrow("an icon of no size paints nothing") { swingIcon.paintIcon(null, g, 0, 0) }
    }
    finally {
      g.dispose()
    }
  }

  @Test
  fun `a file location on a network share has no URL`() {
    // UNC paths, also with mixed separators; images are not loaded from the network
    for (path in listOf("//server/share/icon.svg", "\\\\server\\share\\icon.svg", "/\\server\\share\\icon.svg")) {
      assertEquals(null, FileImageResourceLocation(path).toUrl(), path)
    }
  }

  @Test
  fun `an image file with a space in its path renders the file`() {
    val folder = Files.createDirectories(dir.resolve("with space"))
    val file = redSquare(folder)

    assertEquals(16 * 16, countRed(render(imageFileIconDescriptor(file))))
  }

  @Test
  fun `a file URL renders the file`() {
    val file = redSquare()

    assertEquals(16 * 16, countRed(render(imageUrlIconDescriptor(file.toUri()))))
  }

  @Test
  fun `a jar URL renders the image in the jar`() {
    val url = UrlImageResourceTest::class.java.classLoader.getResource("expui/general/save.svg")!!.toURI()

    val image = render(imageUrlIconDescriptor(url))

    assertTrue(countOpaque(image) > 0) { "the icon at $url must be rendered" }
  }

  @Test
  fun `network URLs are rejected`() {
    for (url in NETWORK_URLS) {
      assertThrows<IllegalArgumentException>("$url must be rejected") { imageUrlIconDescriptor(URI(url)) }
    }
  }

  @Test
  fun `a location with a network URL cannot be created`() {
    for (url in NETWORK_URLS) {
      assertThrows<IllegalArgumentException>("$url must be rejected") { UrlImageResourceLocation(url) }
    }
  }

  @Test
  fun `a location with a network URL cannot be deserialized`() {
    // e.g. a location that the frontend in remote development receives
    val json = Json { serializersModule = (IconManager.getInstance() as DefaultIconManager).getSerializersModule() }
    val serializer = PolymorphicSerializer(ImageResourceLocation::class)
    val localUrl = "file:/icons/icon.svg"
    val localText = json.encodeToString(serializer, UrlImageResourceLocation(localUrl))
    assertEquals(UrlImageResourceLocation(localUrl), json.decodeFromString(serializer, localText))
    for (url in NETWORK_URLS) {
      val text = localText.replace(localUrl, url)
      assertThrows<IllegalArgumentException>("$url must be rejected") { json.decodeFromString(serializer, text) }
    }
  }

  @Test
  fun `a location that is not a valid URI cannot be created`() {
    // e.g. a file path, or a file: URL with an unescaped space, which a URI reader such as Jewel cannot read
    // also a jar: URL that names no entry, and an opaque file: URL, which has no path
    for (url in listOf("/icons/icon.svg", "C:/icons/icon.svg", "file:/with space/icon.svg", "jar:file:/icons/icons.jar", "file:icon.svg")) {
      assertThrows<IllegalArgumentException>("$url must be rejected") { UrlImageResourceLocation(url) }
    }
  }

  private fun redSquare(folder: Path = dir): Path {
    val file = folder.resolve("red.svg")
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

/** Locations on the network, also inside a remote archive. */
private val NETWORK_URLS = listOf(
  "https://example.com/icon.svg",
  "http://example.com/icon.svg",
  "ftp://example.com/icon.svg",
  "jar:https://example.com/icons.jar!/icon.svg",
  "jar:http://example.com/icons.jar!/icon.svg",
  // a network share on Windows
  "file://server/share/icon.svg",
  "jar:file://server/share/icons.jar!/icon.svg",
  // a network share in the form of `File.toURI` on Windows
  "file:////server/share/icon.svg",
  "jar:file:////server/share/icons.jar!/icon.svg",
  // a network share with escaped backslashes, `/\\server\share` after decoding
  "file:/%5C%5Cserver%5Cshare%5Cicon.svg",
  "jar:file:/%5C%5Cserver%5Cshare%5Cicons.jar!/icon.svg",
  "file://localhost/icons/icon.svg",
)
