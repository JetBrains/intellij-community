// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images

import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import org.jetbrains.jps.model.module.JpsModule
import java.awt.image.BufferedImage
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong
import javax.imageio.ImageIO

class ImageSizeOptimizer(private val projectHome: Path) {
  private val optimizedTotal = AtomicLong(0)
  private val totalFiles = AtomicLong(0)

  fun optimizeIcons(module: JpsModule, moduleConfig: IntellijIconClassGeneratorModuleConfig?) {
    val icons = ImageCollector(projectHome, moduleConfig = moduleConfig).collect(module)
    icons.parallelStream().forEach { icon ->
      icon.files.parallelStream().forEach {
        tryToReduceSize(it)
      }
    }
  }

  fun optimizeImages(file: Path): Int {
    if (Files.isDirectory(file)) {
      var count = 0
      file.processChildren {
        count += optimizeImages(it)
      }
      return count
    }
    else {
      val success = tryToReduceSize(file)
      return if (success) 1 else 0
    }
  }

  fun printStats() {
    println()
    println("PNG size optimization: ${optimizedTotal.get()} bytes in total in ${totalFiles.get()} files(s)")
  }

  private fun tryToReduceSize(file: Path): Boolean {
    val image = optimizeImage(file) ?: return false

    totalFiles.incrementAndGet()
    if (image.hasOptimumSize) return true

    try {
      Files.createDirectories(file.parent)
      Files.newOutputStream(file).use { out ->
        out.write(image.optimizedArray.internalBuffer, 0, image.optimizedArray.size())
      }
      optimizedTotal.getAndAdd(image.sizeBefore - image.sizeAfter)
    }
    catch (e: IOException) {
      throw Exception("Cannot optimize $file")
    }
    println("$file ${image.compressionStats}")
    return true
  }

  companion object {
    fun optimizeImage(file: Path): OptimizedImage? {
      if (!file.fileName.toString().endsWith(".png")) {
        return null
      }

      val image = Files.newInputStream(file).buffered().use { ImageIO.read(it) }
      if (image == null) {
        println(file + " loading failed")
        return null
      }

      val byteArrayOutputStream = BufferExposingByteArrayOutputStream()
      ImageIO.write(image, "png", byteArrayOutputStream)
      return OptimizedImage(file, image, byteArrayOutputStream)
    }
  }

  class OptimizedImage(val file: Path, val image: BufferedImage, val optimizedArray: BufferExposingByteArrayOutputStream) {
    val sizeBefore: Long = Files.size(file)
    val sizeAfter: Int = optimizedArray.size()

    val compressionStats: String get() {
      val compression = (sizeBefore - sizeAfter) * 100 / sizeBefore
      return "$compression% optimized ($sizeBefore->$sizeAfter bytes)"
    }

    val hasOptimumSize: Boolean get() = sizeBefore <= sizeAfter
  }
}