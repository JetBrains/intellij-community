// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.preferences

import fleet.util.multiplatform.linkToActual
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

// Paths for resources while running Fleet from sources
// None of its methods should be called on production call path
object FleetFromSourcesPaths {
  val isRunningFromSources: Boolean by lazy {
    !isFleetDistributionMode && findRepositoryRoot() != null
  }

  val intellijProjectRoot: String by lazy {
    requireNotNull(findRepositoryRoot()) {
      "Cannot find IntelliJ repository root"
    }
  }

  val projectRoot: Path by lazy {
    Path(intellijProjectRoot, "fleet")
  }

  // TODO: remove the usages of this property and delete it.
  // Ideally `skiko.library.path` should always be set by the tooling or distribution argfile, making `skikoLibraryDirectory` property redundant.
  // Currently, this is needed for GalleryApp and some isolated UI tests.
  // Once our test running logic is unified, we will be able to wire the proper preparation steps to test runs avoiding such code.
  val skikoLibraryDirectory: Path by lazy {
    val skiko = Path(projectRoot, "build/fleet-skiko/build/skiko/buildPlatform")
    require(skiko.takeIf { SystemFileSystem.exists(it) }?.let { SystemFileSystem.list(it) }?.isNotEmpty() == true) {
      """
        '$skiko' is empty or does not exist.
        
        Usually, this directory is automatically populated when required. However a few use cases are outside of the normal test flow.
        If you are in such case (standalone noria UI tests, etc.), you should either:
         - run `./fleet.sh :fleet-skiko:downloadSkikoForJps` Gradle command
         - or, set the JVM system property `skiko.library.path` to a valid skiko downloaded on your machine
        
        If you ran through a JPS configuration, probably it is misconfigured, please contact #fleet-platform.
        If you ran through the Fleet Gradle build tooling, probably it is misconfigured, please contact #fleet-platform.
      """.trimIndent()
    }
    skiko
  }
}

internal fun findRepositoryRoot(): String? = linkToActual()