// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport

import org.jetbrains.annotations.ApiStatus

/**
 * Reload context that describes modifications in settings files
 * @see [ExternalSystemProjectAware.settingsFiles] for details
 */
@ApiStatus.NonExtendable
interface ExternalSystemSettingsFilesReloadContext {

  /**
   * Aggregated modification type for changed all files.
   */
  val modificationType: ExternalSystemModificationType

  /**
   * Paths of updated files since previous reload.
   */
  val updated: Set<String>

  /**
   * Paths of create files since previous reload.
   */
  val created: Set<String>

  /**
   * Paths of deleted files since previous reload.
   */
  val deleted: Set<String>
}