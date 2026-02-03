// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.images

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.ui.svg.getSvgDocumentSize
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageInputStream

internal val File.children: List<File>
  get() = if (isDirectory) listFiles()?.toList() ?: emptyList() else emptyList()

internal fun isImage(file: Path, iconsOnly: Boolean): Boolean {
  return if (iconsOnly) isIcon(file) else isImage(file)
}

// allow other project path setups to generate Android Icons
var androidIcons: Path = Path.of(PathManager.getCommunityHomePath(), "android/artwork/resources")

internal fun isIcon(file: Path): Boolean {
  val fileName = file.fileName.toString()
  val extension = ImageExtension.fromName(fileName) ?: return false

  if (extension == ImageExtension.SVG) {
    return true
  }
  if (fileName.startsWith("qodana") || file.startsWith(androidIcons)) {
    return true
  }

  val image = try {
    loadPng(file)
  }
  catch (e: Exception) {
    return false
  }

  val width = image.width
  val height = image.height
  return height == width || height <= 100 && width <= 100
}

private fun loadPng(file: Path): BufferedImage {
  val reader = ImageIO.getImageReadersByFormatName("png").next()
  try {
    Files.newInputStream(file).use { fileInput ->
      MemoryCacheImageInputStream(fileInput).use { imageInputStream ->
        reader.setInput(imageInputStream, true, true)
        return reader.read(0, null)
      }
    }
  }
  finally {
    reader.dispose()
  }
}

internal fun isImage(file: Path): Boolean = ImageExtension.fromName(file.fileName.toString()) != null

internal fun imageSize(file: Path, failOnMalformedImage: Boolean = false): Dimension? {
  return try {
    if (file.toString().endsWith(".svg")) {
      val data = Files.readAllBytes(file)
      val size = getSvgDocumentSize(data = data)
      Dimension(size.width.toInt(), size.height.toInt())
    } else {
      val image = loadPng(file)
      val width = image.width
      val height = image.height
      Dimension(width, height)
    }
  }
  catch (e: Exception) {
    if (failOnMalformedImage) {
      throw e
    }
    println("WARNING: can't load $file")
    null
  }
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
      return when {
        name.endsWith(PNG.suffix) -> PNG
        name.endsWith(SVG.suffix) -> SVG
        name.endsWith(GIF.suffix) -> GIF
        else -> null
      }
    }
  }
}