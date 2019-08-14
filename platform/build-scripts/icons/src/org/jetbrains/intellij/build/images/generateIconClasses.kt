// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images

import com.intellij.openapi.application.PathManager
import org.jetbrains.jps.model.serialization.JpsSerializationManager
import java.io.File

fun main(args: Array<String>) = generateIconsClasses()

internal fun generateIconsClasses() {
  val homePath = PathManager.getHomePath()
  val home = File(homePath)
  val project = JpsSerializationManager.getInstance().loadModel(homePath, null).project

  val generator = IconsClassGenerator(home, project.modules)
  project.modules.parallelStream().forEach(generator::processModule)
  generator.printStats()

  val optimizer = ImageSizeOptimizer(home)
  project.modules.parallelStream().forEach(optimizer::optimizeIcons)
  optimizer.printStats()

  val preCompiler = ImageSvgPreCompiler()
  preCompiler.preCompileIcons(project.modules)
  preCompiler.printStats()

  val checker = ImageSanityChecker(home)
  project.modules.forEach { module ->
    checker.check(module)
  }
//  checker.printInfo()
  checker.printWarnings()

  println()
  println("Done")
}