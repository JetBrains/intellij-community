// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.util.JpsPathUtil

import java.util.function.BiPredicate

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
  var additionalModules: MutableList<String> = mutableListOf()

  /**
   * Names of modules for which Maven artifacts should be generated, that will create all its module-dependencies in a single jar.
   *
   * Initially, it's introduced for having `util-base` artifact which will include `util-rt` in it to avoid JPMS package-split.
   */
  var squashedModules: MutableList<String> = mutableListOf()

  /**
   * Names of proprietary modules for which Maven artifacts should be generated.
   *
   *  <p>
   *  Note: Intended only for private Maven repository publication.
   *  </p>
   */
  var proprietaryModules: MutableList<String> = mutableListOf()

  /**
   * A predicate which returns {@code true} for modules which sources should be published as Maven artifacts.
   */
  var publishSourcesFilter: BiPredicate<JpsModule, BuildContext> = BiPredicate { module, context ->
    module.contentRootsList.urls.all { FileUtil.isAncestor(context.paths.communityHome, JpsPathUtil.urlToPath(it), false) }
  }
}
