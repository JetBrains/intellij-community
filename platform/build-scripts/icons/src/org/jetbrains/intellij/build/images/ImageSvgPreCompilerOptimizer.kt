// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images

import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.*
import org.jetbrains.jps.model.module.JpsModule
import java.io.File
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong

/**
 * Works together with [SVGLoaderCache] to generate pre-cached icons
 */
class ImageSvgPreCompilerOptimizer(private val projectHome: File) {
  /// the expected scales of images that we have
  /// the macOS touch bar uses 2.5x scale
  /// the application icon (which one?) is 4x on macOS
  private val scales = doubleArrayOf(1.0, 2.0, 2.5)

  private val generatedSize = AtomicLong(0)
  private val totalFiles = AtomicLong(0)

  fun preCompileIcons(module: JpsModule) {
    val icons = ImageCollector(projectHome.toPath()).collect(module)
    icons.parallelStream().forEach { icon ->
      icon.files.parallelStream().forEach {
        preCompile(it)
      }
    }
  }

  fun printStats() {
    println()
    println("SVG generation: ${StringUtil.formatFileSize(generatedSize.get())} bytes in total in ${totalFiles.get()} files(s)")
  }

  private fun preCompile(file: Path) {
    if (!file.fileName.toString().endsWith(".svg")) {
      return
    }

    totalFiles.incrementAndGet()

    for (scale in scales) {
      val data = file.toFile().readBytes()

      val dim = ImageLoader.Dimension2DDouble(0.0, 0.0)
      val image = SVGLoader.loadWithoutCache(file.toUri().toURL(), data, scale, dim)

      val targetFile = File(file.toFile().path + SVGLoaderPrebuilt.getPreBuiltImageURLSuffix(scale))
      SVGLoaderCacheIO.writeImageFile(targetFile, image, dim)
      val length = targetFile.length()
      require(length > 0)
      generatedSize.addAndGet(length)
    }
  }
}
