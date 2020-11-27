// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.jps.model.module.JpsModule

/**
 * Generates Maven artifacts for Kotlin IDE modules
 */
@CompileStatic
final class KotlinMavenArtifactsBuilder extends MavenArtifactsBuilder {
  private static final Set<String> MODULE_GROUP_NAMES = Set.of("gradle", "uast")

  KotlinMavenArtifactsBuilder(BuildContext buildContext) {
    super(buildContext)
  }

  @Override
  protected boolean shouldSkipModule(String moduleName, boolean moduleIsDependency) {
    if (moduleIsDependency) {
      return moduleName.startsWith("intellij")
    }
    return false
  }

  @Override
  @SuppressWarnings('UnnecessaryQualifiedReference')
  protected MavenArtifactsBuilder.MavenCoordinates generateMavenCoordinatesForModule(JpsModule module) {
    def moduleName = module.name
    def names = moduleName.split("\\.")
    if (names.size() < 2) {
      buildContext.messages.error("Cannot generate Maven artifacts: incorrect module name '${moduleName}'")
    }
    String groupId = "org.jetbrains.kotlin"
    def firstMeaningful = names.size() > 2 && MODULE_GROUP_NAMES.contains(names[1]) ? 2 : 1
    String artifactId = names.drop(firstMeaningful).join("-")
    return new MavenArtifactsBuilder.MavenCoordinates(groupId, artifactId, buildContext.buildNumber)
  }
}
