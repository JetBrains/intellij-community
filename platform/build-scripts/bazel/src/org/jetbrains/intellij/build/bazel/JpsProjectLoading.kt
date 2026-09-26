// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.serialization.JpsPathMapper
import org.jetbrains.jps.model.serialization.JpsProjectLoader
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

internal fun loadJpsProject(projectDir: Path, communityRoot: Path, m2Repo: String): JpsProject {
  val model = JpsElementFactory.getInstance().createModel()
  val kotlinSnapshotRoot = communityRoot.resolve("lib/kotlin-snapshot").invariantSeparatorsPathString
  val pathMapper = JpsPathMapper { url ->
    url?.replace($$"$PROJECT_DIR$/lib/kotlin-snapshot", kotlinSnapshotRoot)
  }
  JpsProjectLoader.loadProject(
    /* project = */ model.project,
    /* pathVariables = */ mapOf("MAVEN_REPOSITORY" to m2Repo),
    /* pathMapper = */ pathMapper,
    /* projectPath = */ projectDir,
    /* loadUnloadedModules = */ true,
    /* externalConfigurationDirectory = */ null,
  )
  return model.project
}
