// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.images

import com.intellij.openapi.application.PathManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.images.sync.jpsProject
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes
import java.nio.file.Files
import java.nio.file.Paths

fun main() {
  val homePath = PathManager.getHomePath()
  val home = Paths.get(homePath)
  val project = jpsProject(homePath)

  val optimizer = ImageSizeOptimizer(home)
  runBlocking(Dispatchers.Default) {
    for (module in project.modules) {
      launch {
        for (root in module.sourceRoots) {
          val imagesDir = root.path.resolve("tips/images")
          if (JavaModuleSourceRootTypes.PRODUCTION.contains(root.rootType) && Files.isDirectory(imagesDir)) {
            val images = optimizer.optimizeImages(imagesDir)
            println("Processed root ${root.file} with $images images")
          }
        }
      }
    }
  }
  optimizer.printStats()

  println("\nDone")
}
