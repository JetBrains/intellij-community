// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images

import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.*
import com.intellij.util.io.isDirectory
import com.intellij.util.io.isFile
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.util.JpsPathUtil
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicLong
import java.util.stream.Collectors
import java.util.stream.Stream

/**
 * Works together with [SVGLoaderCache] to generate pre-cached icons
 */
class ImageSvgPreCompiler(private val projectHome: File) {
  /// the expected scales of images that we have
  /// the macOS touch bar uses 2.5x scale
  /// the application icon (which one?) is 4x on macOS
  private val scales = doubleArrayOf(1.0, 2.0, 2.5)

  private val generatedSize = AtomicLong(0)
  private val totalFiles = AtomicLong(0)

  fun preCompileIcons(modules: List<JpsModule>) {
    val allRoots : List<Path> = modules
      .flatMap { module -> module.sourceRoots }
      .filterNot { root -> root.rootType == JavaResourceRootType.TEST_RESOURCE }
      .filterNot { root -> root.rootType == JavaSourceRootType.TEST_SOURCE }
      .map { sourceRoot ->
        //right now we scan all source roots (probably test sources are good to skip)
        //for example, darkula/laf is in source root, not resources root
        Paths.get(JpsPathUtil.urlToPath(sourceRoot.url))
      }
      .distinct()

    val allIcons = allRoots.parallelStream().flatMap { rootDir ->
      if (!rootDir.isDirectory()) return@flatMap Stream.empty<Path>()
      Files.walk(rootDir).parallel().filter { file ->
        file.fileName.toString().endsWith(".svg") && file.isFile()
      }
    }.collect(Collectors.toSet())

    allIcons.parallelStream().forEach(this::preCompile)
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
