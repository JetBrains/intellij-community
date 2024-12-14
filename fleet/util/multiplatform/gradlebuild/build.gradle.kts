// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
plugins {
  alias(libs.plugins.kotlin.multiplatform)
  id("fleet.project-module-conventions")
  id("fleet.toolchain-conventions")
  id("fleet-build-jps-module-plugin")
  id("fleet.multiplatform-module-conventions")
  alias(libs.plugins.dev.adamko.dokkatoo.html)
}

jpsModule {
  location {
    moduleName = "fleet.util.multiplatform"
    isDefaultSrcFolderCommon = true
  }
}