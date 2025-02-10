// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
plugins {
  alias(libs.plugins.kotlin.multiplatform)
  id("fleet.project-module-conventions")
  id("fleet.toolchain-conventions")
  id("fleet.module-publishing-conventions")
  id("fleet.multiplatform-module-conventions")
  id("fleet.open-source-module-conventions")
  id("fleet-build-jps-module-plugin")
  id("fleet.sdk-repositories-publishing-conventions")
  id("fleet-multiplatform-expects-module")

}

jpsModule {
  location {
    moduleName = "fleet.multiplatform.shims"
    isDefaultSrcFolderCommon = true
  }
}