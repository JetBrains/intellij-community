// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.openapi.Disposable

interface ExternalSystemProjectAware {

  /**
   * Identifier of the controlled project
   */
  val projectId: ExternalSystemProjectId

  /**
   * Project settings files
   * It allows to trace project changes, whenever possible ignores empty changes:
   *  e.g. comments, spaces and etc
   */
  val settingsFiles: Set<String>

  /**
   * Subscribes on update events
   */
  fun subscribe(listener: ExternalSystemProjectRefreshListener, parentDisposable: Disposable)

  /**
   * Refreshes external project model
   */
  fun refreshProject()
}