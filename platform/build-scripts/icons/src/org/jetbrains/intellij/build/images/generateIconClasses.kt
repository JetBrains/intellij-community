// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images

import com.intellij.openapi.application.PathManager
import org.jetbrains.intellij.build.images.sync.jpsProject
import org.jetbrains.jps.model.module.JpsModule
import java.io.File

fun main() = generateIconsClasses()

internal fun generateIconsClasses(homePath: String = PathManager.getHomePath(), modulesFilter: (JpsModule) -> Boolean = { true }) {
  val home = File(homePath)
  val project = jpsProject(homePath)

  val modules = project.modules.filter(modulesFilter)

  val generator = IconsClassGenerator(home, modules)
  modules.parallelStream().forEach(generator::processModule)
  generator.printStats()

  val optimizer = ImageSizeOptimizer(home)
  modules.parallelStream().forEach(optimizer::optimizeIcons)
  optimizer.printStats()

  val preCompiler = ImageSvgPreCompiler()
  preCompiler.preCompileIcons(modules)
  preCompiler.printStats()

  val checker = ImageSanityChecker(home)
  modules.forEach(checker::check)
  checker.printWarnings()

  println()
  println("Done")
}