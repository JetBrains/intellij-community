// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.intellij.build.impl

import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.impl.maven.MavenArtifactsBuilder
import org.jetbrains.intellij.build.impl.maven.MavenCoordinates
import org.jetbrains.jps.model.module.JpsModule

private val MODULE_GROUP_NAMES = java.util.Set.of("gradle", "uast")

/**
 * Generates Maven artifacts for Kotlin IDE modules
 */
class KotlinMavenArtifactsBuilder(context: BuildContext) : MavenArtifactsBuilder(context) {
  override fun shouldSkipModule(moduleName: String, moduleIsDependency: Boolean): Boolean {
    return if (moduleIsDependency) moduleName.startsWith("intellij") else false
  }

  override fun generateMavenCoordinatesForModule(module: JpsModule): MavenCoordinates {
    val moduleName = module.name
    val names = moduleName.split("\\.".toRegex()).dropLastWhile(String::isEmpty)
    if (names.size < 2) {
      context.messages.error("Cannot generate Maven artifacts: incorrect module name '$moduleName'")
    }

    val groupId = "org.jetbrains.kotlin"
    val firstMeaningful = if (names.size > 2 && MODULE_GROUP_NAMES.contains(names[1])) 2 else 1
    val artifactId = names.drop(firstMeaningful).joinToString(separator = "-")
    return MavenCoordinates(groupId, artifactId, context.buildNumber)
  }
}