// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.autoimport

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface ExternalSystemSettingsFilesModificationContext {

  /**
   * Aggregated event for file.
   *
   * For example, events CREATE and UPDATE will be merged into CREATE event, UPDATE and DELETE into DELETE.
   */
  val event: Event

  /**
   * Aggregated modification type for all changed files.
   */
  val modificationType: ExternalSystemModificationType

  /**
   * Current reload status is same for all settings files at one time.
   */
  val reloadStatus: ReloadStatus

  enum class Event { CREATE, UPDATE, DELETE }

  enum class ReloadStatus { IDLE, IN_PROGRESS, JUST_STARTED, JUST_FINISHED }
}