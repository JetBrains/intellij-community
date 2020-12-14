// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images

import com.intellij.openapi.application.PathManager
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.AppScheduledExecutorService
import org.jetbrains.intellij.build.images.sync.jpsProject
import org.jetbrains.jps.model.module.JpsModule
import java.nio.file.Path
import java.nio.file.Paths

fun main(args: Array<String>) {
  try {
    generateIconsClasses(args.firstOrNull()?.let { Paths.get(it) })
  }
  finally {
    shutdownAppScheduledExecutorService()
  }
}

internal abstract class IconsClasses {
  open val homePath: String get() = PathManager.getHomePath()
  open val modules: List<JpsModule> get() = jpsProject(homePath).modules
  open fun generator(home: Path, modules: List<JpsModule>) = IconsClassGenerator(home, modules)
}

private class IntellijIconsClasses : IconsClasses() {
  override val modules: List<JpsModule>
    get() = super.modules.filterNot {
      // TODO: use icon-robots.txt
      it.name.startsWith("fleet")
    }
}

internal fun generateIconsClasses(dbFile: Path?, config: IconsClasses = IntellijIconsClasses()) {
  val home = Paths.get(config.homePath)

  val modules = config.modules

  if (System.getenv("GENERATE_ICONS") != "false") {
    val generator = config.generator(home, modules)
    modules.parallelStream().forEach(generator::processModule)
    generator.printStats()
  }

  if (System.getenv("OPTIMIZE_ICONS") != "false") {
    val optimizer = ImageSizeOptimizer(home)
    modules.parallelStream().forEach(optimizer::optimizeIcons)
    optimizer.printStats()
  }

  if (dbFile != null) {
    val preCompiler = ImageSvgPreCompiler()
    preCompiler.preCompileIcons(modules, dbFile)
  }

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