/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.intellij.build.images

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.jps.model.module.JpsModule
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO

class ImageSizeOptimizer(val projectHome: File) {
  private var optimizedTotal: Long = 0

  fun optimizeIcons(module: JpsModule) {
    val icons = ImageCollector(projectHome).collect(module)
    icons.forEach {
      it.files.values.forEach {
        tryToReduceSize(it)
      }
    }
  }

  fun optimizeImages(file: File): Int {
    if (file.isDirectory) {
      var count = 0
      file.listFiles().forEach {
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
    println("PNG size optimization: $optimizedTotal bytes in total")
  }

  private fun tryToReduceSize(file: File): Boolean {
    val image = optimizeImage(file) ?: return false

    if (image.hasOptimumSize) return true

    try {
      FileUtil.writeToFile(file, image.optimizedArray)
      optimizedTotal += image.sizeBefore - image.sizeAfter
    }
    catch (e: IOException) {
      throw Exception("Cannot optimize " + file.absolutePath)
    }
    println("${file.absolutePath} ${image.compressionStats}")
    return true
  }

  companion object {
    fun optimizeImage(file: File): OptimizedImage? {
      if (!file.name.endsWith(".png")) return null
      val image = ImageIO.read(file)
      if (image == null) {
        println(file.absolutePath + " loading failed")
        return null
      }

      val byteArrayOutputStream = ByteArrayOutputStream()
      ImageIO.write(image, "png", byteArrayOutputStream)

      val byteArray = byteArrayOutputStream.toByteArray()
      return OptimizedImage(file, image, byteArray)
    }
  }

  class OptimizedImage(val file: File, val image: BufferedImage, val optimizedArray: ByteArray) {
    val sizeBefore = file.length()
    val sizeAfter = optimizedArray.size

    val compressionStats: String get() {
      val compression = (sizeBefore - sizeAfter) * 100 / sizeBefore
      return "$compression% optimized ($sizeBefore->$sizeAfter bytes)"
    }

    val hasOptimumSize: Boolean get() = sizeBefore <= sizeAfter
  }
}