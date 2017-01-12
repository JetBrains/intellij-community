/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.build.scripts

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.jps.model.module.JpsModule
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO

class ImageSizeOptimizer(val projectHome: File) {
  private var initialTotal: Long = 0
  private var optimizedTotal: Long = 0

  fun optimizeIcons(module: JpsModule) {
    val icons = ImageCollector(projectHome).collect(module)
    icons.forEach {
      it.files.values.forEach {
        tryToReduceSize(it)
      }
    }
  }

  fun optimizeImages(file: File) {
    if (file.isDirectory) {
      file.listFiles().forEach {
        optimizeImages(it)
      }
    }
    else {
      tryToReduceSize(file)
    }
  }

  fun printStats() {
    println("")
    println("PNG size optimization: ${initialTotal - optimizedTotal} bytes in total")
  }

  private fun tryToReduceSize(file: File) {
    if (!file.name.endsWith(".png")) return
    val image = ImageIO.read(file)
    if (image == null) {
      println(file.absolutePath + " loading failed")
      return
    }
    val byteArrayOutputStream = ByteArrayOutputStream()
    ImageIO.write(image, "png", byteArrayOutputStream)

    val byteArray = byteArrayOutputStream.toByteArray()

    val initialSize = file.length()
    initialTotal += initialSize
    if (initialSize <= byteArray.size) {
      optimizedTotal += initialSize
      return
    }

    optimizedTotal += byteArray.size
    try {
      FileUtil.writeToFile(file, byteArray)
    }
    catch (e: IOException) {
      throw Exception("Cannot optimize " + file.absolutePath)
    }
    val compression = (initialSize - byteArray.size) * 100 / initialSize
    println(file.absolutePath + " $compression% optimized ($initialSize->${byteArray.size} bytes)")
  }
}