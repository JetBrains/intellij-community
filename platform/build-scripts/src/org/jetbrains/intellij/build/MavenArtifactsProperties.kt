// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.util.JpsPathUtil
import java.nio.file.Path

/**
 * Specifies how Maven artifacts for IDE modules should be generated.
 * Public artifacts are generated under {@link BuildPaths#artifactDir}/maven-artifacts directory.
 * Proprietary artifacts are generated under {@link BuildPaths#artifactDir}/proprietary-maven-artifacts directory.
 * @see ProductProperties#mavenArtifacts
 * @see org.jetbrains.intellij.build.impl.MavenArtifactsBuilder#generateMavenArtifacts
 */
class MavenArtifactsProperties {
  /**
   * If {@code true} Maven artifacts are generated for all modules included into the IDE distribution.
   */
  var forIdeModules = false

  /**
   * Names of additional modules for which Maven artifacts should be generated.
   */
  var additionalModules: PersistentList<String> = persistentListOf()

  /**
   * Names of modules for which Maven artifacts should be generated, that will create all its module-dependencies in a single jar.
   *
   * Initially, it's introduced for having `util-base` artifact which will include `util-rt` in it to avoid JPMS package-split.
   */
  var squashedModules: PersistentList<String> = persistentListOf()

  /**
   * Names of proprietary modules for which Maven artifacts should be generated.
   *
   *  <p>
   *  Note: Intended only for private Maven repository publication.
   *  </p>
   */
  var proprietaryModules: PersistentList<String> = persistentListOf()

  /**
   * A predicate which returns {@code true} for modules which sources should be published as Maven artifacts.
   */
  var publishSourcesFilter: (JpsModule, BuildContext) -> Boolean = { module, context ->
    module.contentRootsList.urls.all { Path.of(JpsPathUtil.urlToPath(it)).startsWith(context.paths.communityHomeDir) }
  }
}
