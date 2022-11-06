// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.jps.util.JpsPathUtil
import java.nio.file.Path
import java.util.function.BiPredicate

/**
 * Default bundled plugins for all editions of IntelliJ-based IDE.
 * See also [IDEA_BUNDLED_PLUGINS] and [DEFAULT_BUNDLED_PLUGINS].
 */
val JB_BUNDLED_PLUGINS: PersistentList<String> = DEFAULT_BUNDLED_PLUGINS.addAll(persistentListOf(
  "intellij.laf.macos",
  "intellij.laf.win10",
))

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
  properties.includeIntoSourcesArchiveFilter = BiPredicate { module, context ->
    module.contentRootsList.urls.all { url ->
      Path.of(JpsPathUtil.urlToPath(url)).startsWith(context.paths.communityHomeDir)
    }
  }
}