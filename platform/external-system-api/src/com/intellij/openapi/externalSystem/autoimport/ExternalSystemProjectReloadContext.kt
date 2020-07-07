// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface ExternalSystemProjectReloadContext {

  /**
   * Project reload is submitted explicitly for user
   *
   * Expected, project will be reloaded explicitly if this parameter is `true`
   */
  val isExplicitReload: Boolean

  /**
   * Project has undefined modification
   *
   * Expected, project will fully reloaded uf this parameter is `true`
   */
  val hasUndefinedModifications: Boolean

  /**
   * Reload context that describes modifications in settings files
   * @see [ExternalSystemProjectAware.settingsFiles] for details
   */
  val settingsFilesContext: ExternalSystemSettingsFilesReloadContext
}