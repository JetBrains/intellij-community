// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.images

import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.intellij.build.images.sync.findProjectHomePath
import org.jetbrains.intellij.build.images.sync.jpsProject
import org.jetbrains.jps.model.module.JpsModule
import java.nio.file.Path

fun main() {
  try {
    generateIconClasses()
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
  /**
   * Exclude specified packages from icon processing
   */
  val excludePackages: List<String> = emptyList(),
)

abstract class IconClasses {
  open val homePath: String
    get() = findProjectHomePath()

  open val modules: List<JpsModule>
    get() = jpsProject(homePath).modules

  internal open fun generator(home: Path, modules: List<JpsModule>) = IconsClassGenerator(home, modules)

  open fun getConfigForModule(moduleName: String): IntellijIconClassGeneratorModuleConfig? = null
}

internal fun generateIconClasses(config: IconClasses = IntellijIconClassGeneratorConfig()) {
  val home = Path.of(config.homePath)

  val modules = config.modules
    // Toolbox icons are not based on IJ Platform
    .filter { !it.name.startsWith("toolbox.") }

  // TODO: update copyright into svg icons

  if (System.getenv("OPTIMIZE_ICONS") != "false") {
    val optimizer = ImageSizeOptimizer(home)
    modules.parallelStream().forEach { optimizer.optimizeIcons(it, config.getConfigForModule(it.name)) }
    optimizer.printStats()
  }

  if (System.getenv("GENERATE_ICONS") != "false") {
    val generator = config.generator(home, modules)
    modules.parallelStream().forEach { generator.processModule(it, config.getConfigForModule(it.name)) }
    generator.printStats()
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