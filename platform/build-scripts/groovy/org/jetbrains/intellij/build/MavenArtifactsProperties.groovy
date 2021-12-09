// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import com.intellij.openapi.util.io.FileUtil
import groovy.transform.CompileStatic
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
@CompileStatic
class MavenArtifactsProperties {
  /**
   * If {@code true} Maven artifacts are generated for all modules included into the IDE distribution.
   */
  boolean forIdeModules = false

  /**
   * Names of additional modules for which Maven artifacts should be generated.
   */
  List<String> additionalModules = []

  /**
   * Names of proprietary modules for which Maven artifacts should be generated.
   *
   *  <p>
   *  Note: Intended only for private Maven repository publication.
   *  </p>
   */
  List<String> proprietaryModules = []

  /**
   * A predicate which returns {@code true} for modules which sources should be published as Maven artifacts.
   */
  BiPredicate<JpsModule, BuildContext> publishSourcesFilter = { JpsModule module, BuildContext context ->
    module.contentRootsList.urls.every { FileUtil.isAncestor(context.paths.communityHome, JpsPathUtil.urlToPath(it), false) }
  } as BiPredicate<JpsModule, BuildContext>
}
