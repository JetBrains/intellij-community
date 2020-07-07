// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport

import org.jetbrains.annotations.ApiStatus

/**
 * Reload context that describes modifications in settings files
 * @see [ExternalSystemProjectAware.settingsFiles] for details
 */
@ApiStatus.Experimental
interface ExternalSystemSettingsFilesReloadContext {

  val updated: Set<String>

  val created: Set<String>

  val deleted: Set<String>
}