// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

private val androidIcons by lazy {
  Paths.get(PathManager.getCommunityHomePath(), "android/artwork/resources")
}

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

internal fun imageSize(file: Path): Dimension? {
  val image = loadImage(file)
  if (image == null) {
    println("WARNING: can't load $file")
    return null
  }

  val width = image.width
  val height = image.height
  return Dimension(width, height)
}

private fun loadImage(file: Path): BufferedImage? {
  if (file.toString().endsWith(".svg")) {
    // don't mask any exception for svg file
    Files.newBufferedReader(file).use {
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
    e.printStackTrace(System.out)
    return null
  }
}

internal fun md5(file: Path): String {
  val hash = DigestUtil.md5().digest(Files.readAllBytes(file))
  return BigInteger(hash).abs().toString(16)
}

internal enum class ImageType(private val suffix: String) {
  BASIC(""), RETINA("@2x"), DARCULA("_dark"), RETINA_DARCULA("@2x_dark");

  companion object {
    fun getBasicName(file: Path, prefix: List<String>): String {
      return getBasicName(file.fileName.toString(), prefix)
    }

    fun getBasicName(suffix: String, prefix: List<String>): String {
      val name = FileUtilRt.getNameWithoutExtension(suffix)
      return stripSuffix((prefix + name).joinToString("/"))
    }

    fun fromFile(file: Path): ImageType {
      return fromName(FileUtilRt.getNameWithoutExtension(file.fileName.toString()))
    }

    private fun fromName(name: String): ImageType {
      if (name.endsWith(RETINA_DARCULA.suffix)) return RETINA_DARCULA
      if (name.endsWith(RETINA.suffix)) return RETINA
      if (name.endsWith(DARCULA.suffix)) return DARCULA
      return BASIC
    }

    fun stripSuffix(name: String): String {
      val type = fromName(name)
      return name.removeSuffix(type.suffix)
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