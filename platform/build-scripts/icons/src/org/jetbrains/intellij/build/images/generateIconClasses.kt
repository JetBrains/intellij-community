// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images

import com.intellij.openapi.application.PathManager
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.AppScheduledExecutorService
import org.jetbrains.intellij.build.images.sync.jpsProject
import org.jetbrains.jps.model.module.JpsModule
import java.io.File

fun main() = try {
  generateIconsClasses()
}
finally {
  shutdownAppScheduledExecutorService()
}

internal abstract class IconsClasses {
  open val homePath: String get() = PathManager.getHomePath()
  open val modules: List<JpsModule> get() = jpsProject(homePath).modules
  open fun generator(home: File, modules: List<JpsModule>) = IconsClassGenerator(home, modules)
}

private class IntellijIconsClasses : IconsClasses() {
  override val modules: List<JpsModule>
    get() = super.modules.filterNot {
      // TODO: use icon-robots.txt
      it.name.startsWith("fleet")
    }
}

internal fun generateIconsClasses(config: IconsClasses = IntellijIconsClasses()) {
  val home = File(config.homePath)

  val modules = config.modules

  val generator = config.generator(home, modules)
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

/**
 * Initialized in [com.intellij.util.SVGLoader]
 */
internal fun shutdownAppScheduledExecutorService() {
  try {
    (AppExecutorUtil.getAppScheduledExecutorService() as AppScheduledExecutorService)
      .shutdownAppScheduledExecutorService()
  }
  catch (e: Exception) {
    System.err.println("Failed during executor service shutdown:")
    e.printStackTrace(System.err)
  }
}