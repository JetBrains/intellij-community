// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.jetbrains.intellij.build.ProductModulesLayout.Companion.DEFAULT_BUNDLED_PLUGINS
import org.jetbrains.jps.util.JpsPathUtil
import java.nio.file.Path
import java.util.function.BiPredicate

/**
 * Describes distribution of an in-house IntelliJ-based IDE hosted in IntelliJ repository.
 */
abstract class JetBrainsProductProperties : ProductProperties() {
  init {
    @Suppress("LeakingThis")
    configureJetBrainsProduct(this)
  }

  override suspend fun copyAdditionalFiles(context: BuildContext, targetDirectory: String) {
    copyAdditionalFilesBlocking(context, targetDirectory)
  }

  open fun copyAdditionalFilesBlocking(context: BuildContext, targetDirectory: String) {
  }
}

internal fun configureJetBrainsProduct(properties: ProductProperties) {
  properties.scrambleMainJar = true
  properties.productLayout.bundledPluginModules = DEFAULT_BUNDLED_PLUGINS
    .add("intellij.laf.macos")
    .add("intellij.laf.win10")
    .toMutableList()
  properties.includeIntoSourcesArchiveFilter = BiPredicate { module, context ->
    module.contentRootsList.urls.all { url ->
      Path.of(JpsPathUtil.urlToPath(url)).startsWith(context.paths.communityHomeDir.communityRoot)
    }
  }
}