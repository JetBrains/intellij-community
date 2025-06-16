// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.apache.maven.model.Model
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.impl.maven.GeneratedMavenArtifacts
import org.jetbrains.intellij.build.impl.maven.MavenCoordinates
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.util.JpsPathUtil
import java.nio.file.Path

/**
 * Specifies how Maven artifacts for IDE modules should be generated.
 * Public artifacts are generated under [BuildPaths.artifactDir]/maven-artifacts directory.
 * Proprietary artifacts are generated under [BuildPaths.artifactDir]/proprietary-maven-artifacts directory.
 * @see ProductProperties.mavenArtifacts
 * @see org.jetbrains.intellij.build.impl.maven.MavenArtifactsBuilder.generateMavenArtifacts
 */
class MavenArtifactsProperties {
  /**
   * If `true` Maven artifacts are generated for all modules included in the IDE distribution.
   */
  var forIdeModules: Boolean = false

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
   * A predicate which returns `true` for modules which sources should be published as Maven artifacts.
   */
  var publishSourcesFilter: (JpsModule, BuildContext) -> Boolean = { module, context ->
    module.contentRootsList.urls.all { Path.of(JpsPathUtil.urlToPath(it)).startsWith(context.paths.communityHomeDir) }
  }

  @ApiStatus.Internal
  var patchCoordinates: (JpsModule, MavenCoordinates) -> MavenCoordinates = { _, coordinates -> coordinates }

  @ApiStatus.Internal
  var addPomMetadata: (JpsModule, Model) -> Unit = { _, _ -> }

  @ApiStatus.Internal
  var isJavadocJarRequired: (JpsModule) -> Boolean = { false }

  @ApiStatus.Internal
  var validate: (BuildContext, Collection<GeneratedMavenArtifacts>) -> Unit = { _, _ -> }
}
