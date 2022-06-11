// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport

/**
 * Project listener of specific external system (gradle, maven, sbt or etc.).
 * Needed to highlight bounds of project refresh on the side of an external system.
 */
interface ExternalSystemProjectListener {

  @JvmDefault
  fun onProjectReloadStart() {}

  @JvmDefault
  fun onProjectReloadFinish(status: ExternalSystemRefreshStatus) {}

  @JvmDefault
  fun onSettingsFilesListChange() {}
}