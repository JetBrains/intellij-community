plugins {
  alias(libs.plugins.kotlin.multiplatform)
  id("fleet.project-module-conventions")
  id("fleet.toolchain-conventions")
  id("fleet.module-publishing-conventions")
  id("fleet.open-source-module-conventions")
  id("fleet-build-jps-module-plugin")
  alias(libs.plugins.dev.adamko.dokkatoo.html)
}

jpsModule {
  location {
    moduleName = "fleet.rpc.server"
  }
}