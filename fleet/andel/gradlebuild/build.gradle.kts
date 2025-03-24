// IMPORT__MARKER_START
// IMPORT__MARKER_END
plugins {
  alias(libs.plugins.kotlin.multiplatform)
  id("fleet.project-module-conventions")
  id("fleet.toolchain-conventions")
  id("fleet.module-publishing-conventions")
  id("fleet.open-source-module-conventions")
  id("fleet-build-jps-module-plugin")
  alias(libs.plugins.dokka)
  // GRADLE_PLUGINS__MARKER_START
  // GRADLE_PLUGINS__MARKER_END
}

val jpsModuleName = "fleet.andel"

jpsModule {
  location {
    moduleName = jpsModuleName
  }
}

kotlin {
  // KOTLIN__MARKER_START
  // KOTLIN__MARKER_END
}