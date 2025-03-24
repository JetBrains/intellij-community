// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// IMPORT__MARKER_START
// IMPORT__MARKER_END
plugins {
  alias(libs.plugins.kotlin.multiplatform)
  id("fleet.project-module-conventions")
  id("fleet.toolchain-conventions")
  id("fleet.module-publishing-conventions")
  id("fleet.multiplatform-module-conventions")
  id("fleet.open-source-module-conventions")
  id("fleet-build-jps-module-plugin")
  alias(libs.plugins.dokka)
  id("fleet.sdk-repositories-publishing-conventions")
  id("fleet-multiplatform-expects-module")
  // GRADLE_PLUGINS__MARKER_START
  // GRADLE_PLUGINS__MARKER_END
}

val jpsModuleName = "fleet.multiplatform.shims"

jpsModule {
  location {
    moduleName = jpsModuleName
  }
}

kotlin {
  // KOTLIN__MARKER_START
  // KOTLIN__MARKER_END
}