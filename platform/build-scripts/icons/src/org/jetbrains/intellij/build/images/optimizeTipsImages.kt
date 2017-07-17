package org.jetbrains.intellij.build.images

import com.intellij.openapi.application.PathManager
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes
import org.jetbrains.jps.model.serialization.JpsSerializationManager
import java.io.File

fun main(args: Array<String>) {
  val homePath = PathManager.getHomePath()
  val home = File(homePath)
  val project = JpsSerializationManager.getInstance().loadModel(homePath, null).project

  val optimizer = ImageSizeOptimizer(home)
  project.modules.forEach { module ->
    module.sourceRoots.forEach { root ->
      val imagesDir = File(root.file, "tips/images")
      if (JavaModuleSourceRootTypes.PRODUCTION.contains(root.rootType) && imagesDir.isDirectory) {
        val images = optimizer.optimizeImages(imagesDir)
        println("Processed root ${root.file} with $images images")
      }
    }
  }
  optimizer.printStats()

  println()
  println("Done")
}