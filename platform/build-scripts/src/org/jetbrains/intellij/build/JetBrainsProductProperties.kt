// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.jetbrains.jps.util.JpsPathUtil
import java.nio.file.Path
import java.util.function.BiPredicate

/**
 * Describes distribution of an in-house IntelliJ-based IDE hosted in IntelliJ repository.
 */
abstract class JetBrainsProductProperties : ProductProperties() {
  init {
    scrambleMainJar = true
    productLayout.bundledPluginModules.add("intellij.laf.macos")
    productLayout.bundledPluginModules.add("intellij.laf.win10")
    includeIntoSourcesArchiveFilter = BiPredicate { module, context ->
      module.contentRootsList.urls.all { url ->
        Path.of(JpsPathUtil.urlToPath(url)).startsWith(context.paths.communityHomeDir.communityRoot)
      }
    }
  }
}