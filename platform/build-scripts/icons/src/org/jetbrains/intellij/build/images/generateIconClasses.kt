package org.jetbrains.intellij.build.images

import com.intellij.openapi.application.PathManager
import org.jetbrains.jps.model.serialization.JpsSerializationManager
import java.io.File

fun main(args: Array<String>) {
  val homePath = PathManager.getHomePath()
  val home = File(homePath)
  val project = JpsSerializationManager.getInstance().loadModel(homePath, null).project

  val util = project.modules.find { it.name == "util" } ?: throw IllegalStateException("Can't load module 'util'")

  val generator = IconsClassGenerator(home, util)
  project.modules.forEach { module ->
    generator.processModule(module)
  }
  generator.printStats()

  val optimizer = ImageSizeOptimizer(home)
  project.modules.forEach { module ->
    optimizer.optimizeIcons(module)
  }
  optimizer.printStats()

  val checker = ImageSanityChecker(home)
  project.modules.forEach { module ->
    checker.check(module)
  }
//  checker.printInfo()
  checker.printWarnings()

  println()
  println("Done")
}