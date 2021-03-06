// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

data class IntellijIconClassGeneratorModuleConfig(
  /**
   * The package name for icon class.
   */
  val packageName: String? = null,
  /**
   * The top-level icon class name.
   */
  val className: String? = null,
  /**
   * The directory where icons are located relative to resource root.
   */
  val iconDirectory: String? = null,
) {
}

abstract class IconsClasses {
  open val homePath: String
    get() = PathManager.getHomePath()

  open val modules: List<JpsModule>
    get() = jpsProject(homePath).modules

  internal open fun generator(home: Path, modules: List<JpsModule>) = IconsClassGenerator(home, modules)

  open fun getConfigForModule(moduleName: String): IntellijIconClassGeneratorModuleConfig? = null
}

internal fun generateIconsClasses(dbFile: Path?, config: IconsClasses = IntellijIconClassGeneratorConfig()) {
  val home = Path.of(config.homePath)

  val modules = config.modules

  if (System.getenv("GENERATE_ICONS") != "false") {
    val generator = config.generator(home, modules)
    modules.parallelStream().forEach { generator.processModule(it, config.getConfigForModule(it.name)) }
    generator.printStats()
  }

  if (System.getenv("OPTIMIZE_ICONS") != "false") {
    val optimizer = ImageSizeOptimizer(home)
    modules.parallelStream().forEach { optimizer.optimizeIcons(it, config.getConfigForModule(it.name)) }
    optimizer.printStats()
  }

  if (dbFile != null) {
    val preCompiler = ImageSvgPreCompiler()
    preCompiler.preCompileIcons(modules, dbFile)
  }

  val checker = ImageSanityChecker(home)
  modules.parallelStream().forEach { checker.check(it, config.getConfigForModule(it.name)) }
  checker.printWarnings()

  println("\nDone")
}

/**
 * Initialized in [com.intellij.util.SVGLoader]
 */
internal fun shutdownAppScheduledExecutorService() {
  try {
    AppExecutorUtil.shutdownApplicationScheduledExecutorService()
  }
  catch (e: Exception) {
    System.err.println("Failed during executor service shutdown:")
    e.printStackTrace(System.err)
  }
}