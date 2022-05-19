// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl;

import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.jetbrains.intellij.build.BuildContext;
import org.jetbrains.jps.model.module.JpsModule;

import java.util.Set;

/**
 * Generates Maven artifacts for Kotlin IDE modules
 */
public final class KotlinMavenArtifactsBuilder extends MavenArtifactsBuilder {
  public KotlinMavenArtifactsBuilder(BuildContext buildContext) {
    super(buildContext);
  }

  @Override
  protected boolean shouldSkipModule(String moduleName, boolean moduleIsDependency) {
    if (moduleIsDependency) {
      return moduleName.startsWith("intellij");
    }

    return false;
  }

  @Override
  @SuppressWarnings("UnnecessaryQualifiedReference")
  protected MavenCoordinates generateMavenCoordinatesForModule(JpsModule module) {
    final String moduleName = module.getName();
    String[] names = moduleName.split("\\.");
    if (DefaultGroovyMethods.size(names) < 2) {
      buildContext.getMessages().error("Cannot generate Maven artifacts: incorrect module name \'" + moduleName + "\'");
    }

    String groupId = "org.jetbrains.kotlin";
    Integer firstMeaningful = DefaultGroovyMethods.size(names) > 2 && MODULE_GROUP_NAMES.contains(names[1]) ? 2 : 1;
    String artifactId = DefaultGroovyMethods.join(DefaultGroovyMethods.drop(names, firstMeaningful), "-");
    return new MavenCoordinates(groupId, artifactId, buildContext.getBuildNumber());
  }

  private static final Set<String> MODULE_GROUP_NAMES = Set.of("gradle", "uast");
}
