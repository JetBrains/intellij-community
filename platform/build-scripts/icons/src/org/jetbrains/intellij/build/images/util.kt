// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.ui.svg.SvgTranscoder
import com.intellij.ui.svg.createSvgDocument
import com.intellij.util.io.DigestUtil
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.imageio.ImageIO

internal val File.children: List<File>
  get() = if (isDirectory) listFiles()?.toList() ?: emptyList() else emptyList()

internal fun isImage(file: Path, iconsOnly: Boolean): Boolean {
  return if (iconsOnly) isIcon(file) else isImage(file)
}
// allow other project path setups to generate Android Icons
var androidIcons: Path = Paths.get(PathManager.getCommunityHomePath(), "android/artwork/resources")

internal fun isIcon(file: Path): Boolean {
  if (!isImage(file)) {
    return false
  }

  val size = imageSize(file) ?: return false
  if (file.startsWith(androidIcons)) {
    return true
  }
  return size.height == size.width || size.height <= 100 && size.width <= 100
}

internal fun isImage(file: Path) = ImageExtension.fromName(file.fileName.toString()) != null

internal fun isImage(file: File) = ImageExtension.fromName(file.name) != null

internal fun imageSize(file: Path, failOnMalformedImage: Boolean = false): Dimension? {
  val image = try {
    loadImage(file, failOnMalformedImage)
  }
  catch (e: Exception) {
    if (failOnMalformedImage) throw e
    null
  }
  if (image == null) {
    if (failOnMalformedImage) error("Can't load $file")
    println("WARNING: can't load $file")
    return null
  }

  val width = image.width
  val height = image.height
  return Dimension(width, height)
}

private fun loadImage(file: Path, failOnMalformedImage: Boolean): BufferedImage? {
  if (file.toString().endsWith(".svg")) {
    // don't mask any exception for svg file
    Files.newInputStream(file).use {
      try {
        return SvgTranscoder.createImage(1f, createSvgDocument(null, it), null)
      }
      catch (e: Exception) {
        throw IOException("Cannot decode $file", e)
      }
    }
  }

  try {
    return Files.newInputStream(file).buffered().use {
       ImageIO.read(it)
    }
  }
  catch (e: Exception) {
    if (failOnMalformedImage) throw e
    return null
  }
}

internal fun md5(file: Path): String {
  val hash = DigestUtil.md5().digest(Files.readAllBytes(file))
  return BigInteger(hash).abs().toString(16)
}

internal enum class ImageType(private val suffix: String) {
  BASIC(""), RETINA("@2x"), DARCULA("_dark"), RETINA_DARCULA("@2x_dark"), STROKE("_stroke");

  companion object {
    fun getBasicName(suffix: String, prefix: String): String {
      return "$prefix/${stripSuffix(FileUtilRt.getNameWithoutExtension(suffix))}"
    }

    fun fromFile(file: Path): ImageType {
      return fromName(FileUtilRt.getNameWithoutExtension(file.fileName.toString()))
    }

    private fun fromName(name: String): ImageType {
      return when {
        name.endsWith(RETINA_DARCULA.suffix) -> RETINA_DARCULA
        name.endsWith(RETINA.suffix) -> RETINA
        name.endsWith(DARCULA.suffix) -> DARCULA
        name.endsWith(STROKE.suffix) -> STROKE
        else -> BASIC
      }
    }

    fun stripSuffix(name: String): String {
      return name.removeSuffix(fromName(name).suffix)
    }
  }
}

internal enum class ImageExtension(private val suffix: String) {
  PNG(".png"), SVG(".svg"), GIF(".gif");

  companion object {
    fun fromFile(file: Path) = fromName(file.fileName.toString())

    fun fromName(name: String): ImageExtension? {
      if (name.endsWith(PNG.suffix)) return PNG
      if (name.endsWith(SVG.suffix)) return SVG
      if (name.endsWith(GIF.suffix)) return GIF
      return null
    }
  }
}