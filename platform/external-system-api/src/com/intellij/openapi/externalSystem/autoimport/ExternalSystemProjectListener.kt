// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.autoimport

/**
 * Project listener of specific external system (gradle, maven, sbt or etc.).
 * Needed to highlight bounds of project refresh on the side of an external system.
 */
interface ExternalSystemProjectListener {

  fun onProjectReloadStart() {}

  fun onProjectReloadFinish(status: ExternalSystemRefreshStatus) {}

  fun onSettingsFilesListChange() {}
}