// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images

import com.intellij.openapi.application.PathManager
import org.jetbrains.intellij.build.images.sync.jpsProject
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes
import java.io.File
import java.nio.file.Files

fun main() {
  val homePath = PathManager.getHomePath()
  val home = File(homePath)
  val project = jpsProject(homePath)

  val optimizer = ImageSizeOptimizer(home)
  project.modules.forEach { module ->
    module.sourceRoots.forEach { root ->
      val imagesDir = root.file.toPath().resolve("tips/images")
      if (JavaModuleSourceRootTypes.PRODUCTION.contains(root.rootType) && Files.isDirectory(imagesDir)) {
        val images = optimizer.optimizeImages(imagesDir)
        println("Processed root ${root.file} with $images images")
      }
    }
  }
  optimizer.printStats()

  println()
  println("Done")
}