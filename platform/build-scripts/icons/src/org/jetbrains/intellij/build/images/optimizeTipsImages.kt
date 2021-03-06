// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images

import com.intellij.openapi.application.PathManager
import org.jetbrains.intellij.build.images.sync.jpsProject
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes
import org.jetbrains.jps.util.JpsPathUtil
import java.nio.file.Files
import java.nio.file.Paths

fun main() {
  val homePath = PathManager.getHomePath()
  val home = Paths.get(homePath)
  val project = jpsProject(homePath)

  val optimizer = ImageSizeOptimizer(home)
  project.modules.parallelStream().forEach { module ->
    module.sourceRoots.forEach { root ->
      val imagesDir = Paths.get(JpsPathUtil.urlToPath(root.url)).resolve("tips/images")
      if (JavaModuleSourceRootTypes.PRODUCTION.contains(root.rootType) && Files.isDirectory(imagesDir)) {
        val images = optimizer.optimizeImages(imagesDir)
        println("Processed root ${root.file} with $images images")
      }
    }
  }
  optimizer.printStats()

  println("\nDone")
}