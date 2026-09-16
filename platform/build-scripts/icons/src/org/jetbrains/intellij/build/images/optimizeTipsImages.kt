// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.images

import com.intellij.openapi.application.PathManager
import org.jetbrains.intellij.build.images.sync.jpsProject
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.Callable
import java.util.concurrent.Executors

fun main() {
  val homePath = PathManager.getHomePath()
  val home = Paths.get(homePath)
  val project = jpsProject(homePath)

  val optimizer = ImageSizeOptimizer(home)
  Executors.newVirtualThreadPerTaskExecutor().use { executor ->
    val tasks = project.modules.map { module ->
      Callable {
        for (root in module.sourceRoots) {
          val imagesDir = root.path.resolve("tips/images")
          if (JavaModuleSourceRootTypes.PRODUCTION.contains(root.rootType) && Files.isDirectory(imagesDir)) {
            val images = optimizer.optimizeImages(imagesDir)
            println("Processed root ${root.file} with $images images")
          }
        }
      }
    }
    for (future in executor.invokeAll(tasks)) {
      future.get()
    }
  }
  optimizer.printStats()

  println("\nDone")
}
